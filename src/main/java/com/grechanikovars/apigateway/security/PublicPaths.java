package com.grechanikovars.apigateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * The requests that need no token: log in, refresh a token and register.
 *
 * <p>The task says that every endpoint except login and register is protected. The refresh is
 * a third public request on purpose: its credential is the refresh token in the body, and the
 * caller comes here exactly because the access token has expired. Each path is open for
 * {@code POST} only, another method on the same path stays closed.
 *
 * <p>On these paths the {@code Authorization} header is not looked at (see
 * {@code SecurityConfig}): an old token that a client sends along must not turn a login into a
 * 401.
 */
public final class PublicPaths {

  /** Matches the public requests. */
  public static final ServerWebExchangeMatcher MATCHER = ServerWebExchangeMatchers.pathMatchers(
      HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/register");

  private PublicPaths() {
  }
}
