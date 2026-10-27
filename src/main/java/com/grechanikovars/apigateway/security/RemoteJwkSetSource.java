package com.grechanikovars.apigateway.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * The source of the public keys that verify the access tokens: it reads the JSON Web Key Set of
 * the Authentication Service without ever blocking a thread of the event loop.
 *
 * <p>It is not the default source of {@code NimbusReactiveJwtDecoder.withJwkSetUri(...)}, for
 * three reasons. That source fetches the key set again for every token with an unknown
 * {@code kid}, and anybody can send such a token without logging in, so the Gateway would call
 * the Authentication Service for each of them. It uses a {@code WebClient} without timeouts. And
 * it has no limit for the size of the answer. This class keeps the rules of the servlet services:
 *
 * <ul>
 *   <li>the connection and the answer are limited to {@link #TIMEOUT} each, the whole request to
 *       {@link #REQUEST_TIMEOUT}, the answer to {@link #SIZE_LIMIT_BYTES};
 *   <li>the key set is cached for {@link #CACHE_TTL}, the {@code max-age} of the Authentication
 *       Service;
 *   <li>a token with an unknown key causes a new fetch at most once per
 *       {@link #MIN_REFRESH_INTERVAL}, counted from the last fetch; the other such tokens are
 *       rejected without a call;
 *   <li>concurrent requests that need the key set share one call;
 *   <li>nothing is fetched at startup: the first token triggers the first fetch, so the start
 *       order of the services does not matter.
 * </ul>
 *
 * <p>Any failure (the service is down, slow, answers with an error, with too much data or with
 * something that is not a key set) is an error of the returned {@link Flux}. The decoder turns
 * it into a rejected token (see {@link FailClosedReactiveJwtDecoder}). The messages of those
 * errors contain the address of the key set, so they are never logged or sent.
 */
public final class RemoteJwkSetSource implements Function<SignedJWT, Flux<JWK>> {

  /** Time to connect to the Authentication Service and time to wait for its answer. */
  static final Duration TIMEOUT = Duration.ofSeconds(2);

  /** The longest time of the whole request, as a safety net above the two timeouts. */
  static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  /** The largest answer that is read; a key set with one key is about half a kilobyte. */
  static final int SIZE_LIMIT_BYTES = 64 * 1024;

  /**
   * How long the key set is used before it is fetched again. The Authentication Service sends
   * {@code max-age=300}, the same five minutes.
   */
  static final Duration CACHE_TTL = Duration.ofMinutes(5);

  /** The shortest time between a fetch and the next fetch that an unknown key asks for. */
  static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(30);

  private final URI jwksUri;
  private final WebClient webClient;
  private final Clock clock;

  private final AtomicReference<CachedKeys> cache = new AtomicReference<>();
  private final AtomicReference<Instant> lastFetch = new AtomicReference<>();
  private final AtomicReference<Mono<JWKSet>> inFlight = new AtomicReference<>();

  /**
   * Creates the source with its own {@code WebClient} (timeouts and size limit included).
   *
   * @param jwksUri the address of the key set
   * @param clock the application clock; it decides when the cache expires
   * @throws IllegalArgumentException if the address is not a valid URI
   */
  public RemoteJwkSetSource(String jwksUri, Clock clock) {
    this(jwksUri, clock, createWebClient());
  }

  RemoteJwkSetSource(String jwksUri, Clock clock, WebClient webClient) {
    this.jwksUri = URI.create(jwksUri);
    this.clock = clock;
    this.webClient = webClient;
  }

  /**
   * Finds the keys that may have signed a token.
   *
   * @param jwt the token whose header names the key
   * @return the matching keys; empty if there are none, an error if the key set is needed and
   *     cannot be fetched
   */
  @Override
  public Flux<JWK> apply(SignedJWT jwt) {
    return Flux.defer(() -> {
      JWKSelector selector = new JWKSelector(JWKMatcher.forJWSHeader(jwt.getHeader()));
      Instant now = clock.instant();
      CachedKeys cached = cache.get();
      if (cached != null && now.isBefore(cached.expiresAt())) {
        List<JWK> matches = selector.select(cached.keys());
        if (!matches.isEmpty() || !refreshAllowed(now)) {
          return Flux.fromIterable(matches);
        }
      }
      return fetch().flatMapMany(keys -> Flux.fromIterable(selector.select(keys)));
    });
  }

  private boolean refreshAllowed(Instant now) {
    Instant last = lastFetch.get();
    return last == null || !now.isBefore(last.plus(MIN_REFRESH_INTERVAL));
  }

  /** Returns the running fetch, or starts one; all callers of the same moment share it. */
  private Mono<JWKSet> fetch() {
    while (true) {
      Mono<JWKSet> running = inFlight.get();
      if (running != null) {
        return running;
      }
      Mono<JWKSet> started = Mono.defer(this::download)
          .doFinally(signal -> inFlight.set(null))
          .cache();
      if (inFlight.compareAndSet(null, started)) {
        return started;
      }
    }
  }

  private Mono<JWKSet> download() {
    lastFetch.set(clock.instant());
    return webClient.get()
        .uri(jwksUri)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(REQUEST_TIMEOUT)
        .map(RemoteJwkSetSource::parse)
        .doOnNext(keys -> cache.set(new CachedKeys(keys, clock.instant().plus(CACHE_TTL))));
  }

  private static JWKSet parse(String body) {
    try {
      return JWKSet.parse(body);
    } catch (ParseException e) {
      throw new IllegalStateException("The answer is not a key set", e);
    }
  }

  private static WebClient createWebClient() {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(TIMEOUT.toMillis()))
        .responseTimeout(TIMEOUT);
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(SIZE_LIMIT_BYTES))
        .build();
  }

  private record CachedKeys(JWKSet keys, Instant expiresAt) {
  }
}
