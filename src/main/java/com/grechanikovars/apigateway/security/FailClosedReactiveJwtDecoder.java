package com.grechanikovars.apigateway.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * Makes every failure of the decoder a rejected token.
 *
 * <p>Spring Security separates a token that is wrong ({@link BadJwtException}, the client gets
 * 401) from a token that could not be checked. In the reactive decoder the second case is worse
 * than in the servlet one: when the key set cannot be fetched, the builder reports an
 * {@code IllegalStateException}, which is not even a {@code JwtException}. Neither the
 * authentication manager nor {@code AuthenticationWebFilter} turns it into a 401, so the client
 * would get a 500 with a stack trace in the log.
 *
 * <p>The Gateway has no reason to treat the two differently: a token that cannot be checked is
 * not accepted, and the client gets the same 401 as for a wrong token. Everything that is not a
 * {@link BadJwtException} becomes one (also a bug in a validator: it fails closed). The failure
 * is logged as a warning with the class names only, because the messages of the underlying
 * exceptions contain the address of the key set.
 */
public final class FailClosedReactiveJwtDecoder implements ReactiveJwtDecoder {

  /** The message of the exception for a token that could not be checked; it is a constant. */
  public static final String MESSAGE = "The token could not be verified";

  private static final Logger LOGGER = LogManager.getLogger(FailClosedReactiveJwtDecoder.class);

  private final ReactiveJwtDecoder delegate;

  /**
   * Wraps a decoder.
   *
   * @param delegate the decoder that does the work
   */
  public FailClosedReactiveJwtDecoder(ReactiveJwtDecoder delegate) {
    this.delegate = delegate;
  }

  /**
   * Decodes and verifies a token.
   *
   * @param token the compact serialized token
   * @return the verified token, or an error that is always a {@link BadJwtException}
   */
  @Override
  public Mono<Jwt> decode(String token) {
    return Mono.defer(() -> delegate.decode(token))
        .onErrorMap(error -> !(error instanceof BadJwtException), error -> {
          Throwable cause = error.getCause();
          LOGGER.warn("A token could not be verified, the key set is probably not available: {} {}",
              error.getClass().getSimpleName(),
              cause == null ? "" : cause.getClass().getSimpleName());
          return new BadJwtException(MESSAGE, error);
        });
  }
}
