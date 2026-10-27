package com.grechanikovars.apigateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Answers the two security failures of the filter chain, 401 and 403, as RFC 9457 problems.
 *
 * <p>The texts are constants and the message of the exception is never sent: the caller learns
 * nothing about why the request was refused. A missing token, a wrong one, an expired one and
 * one that could not be checked all look the same. The other services use the same two texts.
 */
public class ProblemDetailSecurityHandler
    implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

  /** The detail of every 401 answer. */
  public static final String AUTHENTICATION_REQUIRED = "Authentication is required";

  /** The detail of every 403 answer. */
  public static final String ACCESS_DENIED = "Access is denied";

  private final ProblemDetailWriter writer;

  /**
   * Creates the handler.
   *
   * @param writer writes the problem into the response
   */
  public ProblemDetailSecurityHandler(ProblemDetailWriter writer) {
    this.writer = writer;
  }

  /** Answers a request that needs authentication: 401. */
  @Override
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
    return writer.write(exchange, HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED);
  }

  /** Answers a request of an authenticated caller that has no right to it: 403. */
  @Override
  public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
    return writer.write(exchange, HttpStatus.FORBIDDEN, ACCESS_DENIED);
  }
}
