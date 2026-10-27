package com.grechanikovars.apigateway.config;

import com.grechanikovars.apigateway.security.ProblemDetailSecurityHandler;
import com.grechanikovars.apigateway.security.ProblemDetailWriter;
import com.grechanikovars.apigateway.security.PublicPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

/**
 * Spring Security setup of the Gateway: a stateless resource server that is closed by default.
 *
 * <p>The Gateway is an API for other programs, so there are no sessions, no CSRF protection (it
 * protects cookie sessions, and there are none), no login form, no HTTP Basic and no logout
 * page. Callers bring an access token of the Authentication Service in the header
 * {@code Authorization: Bearer ...}. It is verified here (see {@link JwtConfig}) and the claim
 * {@code role} becomes the authority.
 *
 * <p>The rules, from the first that matches:
 *
 * <ol>
 *   <li>{@link PublicPaths}: log in, refresh and register need no token;
 *   <li>{@code POST /api/auth/validate}: any caller with a role;
 *   <li>{@code /api/users/**} and {@code /api/cards/**}: any caller with a role. Which role may do
 *       what, and whose data it is, is decided by the User Service, which checks the token again;
 *   <li>everything else is denied, also paths that do not exist and the internal paths. An
 *       anonymous caller gets 401, an authenticated one 403.
 * </ol>
 *
 * <p>A token with a role that is not exactly {@code ADMIN} or {@code USER} is authenticated but
 * has no authority, so it opens nothing.
 *
 * <p>Both answers of the chain, 401 and 403, are written as {@code ProblemDetail} by
 * {@link ProblemDetailSecurityHandler}, also the 401 for a bad token, so a missing, a wrong, an
 * expired and an unverifiable token look the same. The chain runs before the routing of Spring
 * Cloud Gateway, so a request that is denied here is never forwarded.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

  /**
   * Builds the one security filter chain of the Gateway.
   *
   * @param http the builder provided by Spring Security
   * @param problemWriter writes the 401 and 403 answers
   * @param jwtDecoder verifies the access tokens
   * @param jwtConverter turns a verified token into an authentication with its role
   * @return the chain that handles every request
   */
  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
      ProblemDetailWriter problemWriter,
      ReactiveJwtDecoder jwtDecoder,
      ReactiveJwtAuthenticationConverterAdapter jwtConverter) {
    ProblemDetailSecurityHandler errorHandler = new ProblemDetailSecurityHandler(problemWriter);
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .exceptionHandling(handling -> handling
            .authenticationEntryPoint(errorHandler)
            .accessDeniedHandler(errorHandler))
        .oauth2ResourceServer(resourceServer -> resourceServer
            .authenticationEntryPoint(errorHandler)
            .accessDeniedHandler(errorHandler)
            .bearerTokenConverter(bearerTokenConverter())
            .jwt(jwt -> jwt
                .jwtDecoder(jwtDecoder)
                .jwtAuthenticationConverter(jwtConverter)))
        .authorizeExchange(exchanges -> exchanges
            .matchers(PublicPaths.MATCHER).permitAll()
            .pathMatchers(HttpMethod.POST, "/api/auth/validate").hasAnyRole("ADMIN", "USER")
            .pathMatchers("/api/users/**", "/api/cards/**").hasAnyRole("ADMIN", "USER")
            .anyExchange().denyAll())
        .build();
  }

  /**
   * Reads the bearer token of a request, except on the public paths.
   *
   * <p>Spring Security answers 401 to every request with a bad token, also on a path that needs
   * none. A client that sends its expired access token along with the refresh request would
   * never get a new one. On the public paths the header is therefore ignored.
   */
  private static ServerAuthenticationConverter bearerTokenConverter() {
    ServerBearerTokenAuthenticationConverter bearer = new ServerBearerTokenAuthenticationConverter();
    return exchange -> PublicPaths.MATCHER.matches(exchange)
        .flatMap(match -> match.isMatch() ? Mono.<Authentication>empty() : bearer.convert(exchange));
  }
}
