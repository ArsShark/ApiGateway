package com.grechanikovars.apigateway.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grechanikovars.apigateway.JwksTestServer;
import com.grechanikovars.apigateway.LogCapture;
import com.grechanikovars.apigateway.MutableClock;
import com.grechanikovars.apigateway.TestKeys;
import com.grechanikovars.apigateway.TestTokens;
import com.grechanikovars.apigateway.config.JwtConfig;
import com.grechanikovars.apigateway.config.SecurityProperties;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Tests the decoder of {@link JwtConfig} together with {@link RemoteJwkSetSource} against a real
 * HTTP server that plays the key set endpoint of the Authentication Service: what is accepted,
 * what is rejected, and how often the key set is fetched.
 */
class JwtVerificationTest {

  private static final Instant START = TestTokens.ISSUED_AT.plus(Duration.ofMinutes(5));
  private static final Duration WAIT = Duration.ofSeconds(10);

  private JwksTestServer server;
  private MutableClock clock;
  private ReactiveJwtDecoder decoder;

  static Stream<Arguments> rejectedTokens() {
    KeyPair other = TestKeys.generateRsa(2048);
    return Stream.of(
        Arguments.of("not a token", (Supplier<String>) () -> "not-a-token"),
        Arguments.of("another issuer",
            (Supplier<String>) () -> TestTokens.access().issuer("somebody-else").build()),
        Arguments.of("a refresh token",
            (Supplier<String>) () -> TestTokens.access().tokenType("refresh").build()),
        Arguments.of("no token type",
            (Supplier<String>) () -> TestTokens.access().tokenType(null).build()),
        Arguments.of("no expiry",
            (Supplier<String>) () -> TestTokens.access().expiresAt(null).build()),
        Arguments.of("another key with the known key id",
            (Supplier<String>) () -> TestTokens.access().signedWith(other).build()));
  }

  @BeforeEach
  void startServer() {
    server = JwksTestServer.start(TestKeys.jwksJson());
    clock = new MutableClock(START);
    decoder = new JwtConfig()
        .jwtDecoder(new SecurityProperties(server.jwksUri(), TestTokens.ISSUER), clock);
  }

  @AfterEach
  void stopServer() {
    server.stop();
  }

  @Test
  void aValidTokenIsAcceptedAndNothingIsFetchedBeforeTheFirstToken() {
    assertEquals(0, server.requestCount());

    Jwt jwt = decoder.decode(TestTokens.access().build()).block(WAIT);

    assertNotNull(jwt);
    assertAll(
        () -> assertEquals("1", jwt.getSubject()),
        () -> assertEquals("USER", jwt.getClaimAsString("role")),
        () -> assertEquals(7L, ((Number) jwt.getClaim("userId")).longValue()),
        () -> assertEquals(1, server.requestCount()));
  }

  @Test
  void theTimeComesFromTheClockAndThereIsNoSkew() {
    String token = TestTokens.access().build();
    Instant expiry = TestTokens.ISSUED_AT.plus(TestTokens.ACCESS_TTL);

    clock.set(expiry.minusSeconds(1));
    assertNotNull(decoder.decode(token).block(WAIT));

    clock.set(expiry.plusSeconds(1));
    assertRejected(token);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedTokens")
  void aWrongTokenIsRejected(String name, Supplier<String> token) {
    assertRejected(token.get());
  }

  @Test
  void theKeySetIsFetchedOnceAndCached() {
    String token = TestTokens.access().build();

    decoder.decode(token).block(WAIT);
    decoder.decode(token).block(WAIT);
    decoder.decode(token).block(WAIT);

    assertEquals(1, server.requestCount());
  }

  @Test
  void theKeySetIsFetchedAgainAfterTheCacheExpires() {
    String token = TestTokens.access().build();
    decoder.decode(token).block(WAIT);

    clock.advance(RemoteJwkSetSource.CACHE_TTL.plusSeconds(1));
    decoder.decode(token).block(WAIT);

    assertEquals(2, server.requestCount());
  }

  @Test
  void anUnknownKeyIdDoesNotCallTheServiceAgainBeforeTheMinimumInterval() {
    decoder.decode(TestTokens.access().build()).block(WAIT);
    String unknownKey = TestTokens.access().keyId("an-unknown-key").build();

    assertRejected(unknownKey);
    assertRejected(unknownKey);
    assertEquals(1, server.requestCount());

    clock.advance(RemoteJwkSetSource.MIN_REFRESH_INTERVAL.plusSeconds(1));
    assertRejected(unknownKey);
    assertEquals(2, server.requestCount());
  }

  @Test
  void aRotatedKeyIsPickedUpAfterTheMinimumInterval() {
    decoder.decode(TestTokens.access().build()).block(WAIT);
    KeyPair rotated = TestKeys.generateRsa(2048);
    server.respondWith(200, TestKeys.jwksJson(rotated));
    String token = TestTokens.access()
        .signedWith(rotated)
        .keyId(TestKeys.thumbprint((RSAPublicKey) rotated.getPublic()))
        .build();

    assertRejected(token);
    assertEquals(1, server.requestCount());

    clock.advance(RemoteJwkSetSource.MIN_REFRESH_INTERVAL.plusSeconds(1));
    assertNotNull(decoder.decode(token).block(WAIT));
    assertEquals(2, server.requestCount());
  }

  @Test
  void concurrentRequestsOnAColdCacheShareOneFetch() {
    String token = TestTokens.access().build();

    List<Jwt> jwts = Flux.range(0, 8)
        .flatMap(i -> Mono.defer(() -> decoder.decode(token)).subscribeOn(Schedulers.parallel()))
        .collectList()
        .block(WAIT);

    assertNotNull(jwts);
    assertAll(
        () -> assertEquals(8, jwts.size()),
        () -> assertEquals(1, server.requestCount()));
  }

  @Test
  void aStoppedServiceGivesTheConstantRejection() {
    server.stop();

    StepVerifier.create(decoder.decode(TestTokens.access().build()))
        .expectErrorSatisfies(error -> assertAll(
            () -> assertTrue(error instanceof BadJwtException),
            () -> assertEquals(FailClosedReactiveJwtDecoder.MESSAGE, error.getMessage())))
        .verify(WAIT);
  }

  @Test
  void aServerErrorIsRejectedAndTheNextRequestTriesAgain() {
    String token = TestTokens.access().build();
    server.respondWith(500, "");

    assertRejected(token);
    assertEquals(1, server.requestCount());

    server.respondWith(200, TestKeys.jwksJson());
    assertNotNull(decoder.decode(token).block(WAIT));
    assertEquals(2, server.requestCount());
  }

  @Test
  void aSlowServiceIsGivenUpAfterTheTimeout() {
    server.hang();
    long started = System.nanoTime();

    assertRejected(TestTokens.access().build());

    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
    assertTrue(elapsed.compareTo(Duration.ofSeconds(4)) < 0,
        String.format("The rejection took %s", elapsed));
  }

  @Test
  void anAnswerOverTheSizeLimitIsRejected() {
    String padding = "a".repeat(RemoteJwkSetSource.SIZE_LIMIT_BYTES + 1024);
    server.respondWith(200, String.format("{\"keys\":[],\"padding\":\"%s\"}", padding));

    assertRejected(TestTokens.access().build());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"hello\":1}", "this is not json"})
  void anAnswerThatIsNotAKeySetIsRejected(String answer) {
    server.respondWith(200, answer);

    assertRejected(TestTokens.access().build());
  }

  @Test
  void theLogNeverHoldsTheAddressOfTheKeySet() {
    String address = server.jwksUri();
    server.stop();

    try (LogCapture capture = LogCapture.attach()) {
      assertRejected(TestTokens.access().build());

      assertAll(
          () -> assertTrue(capture.contains("A token could not be verified")),
          () -> assertFalse(capture.contains(address)),
          () -> assertFalse(capture.contains("jwks.json")));
    }
  }

  private void assertRejected(String token) {
    StepVerifier.create(decoder.decode(token)).expectError(BadJwtException.class).verify(WAIT);
  }
}
