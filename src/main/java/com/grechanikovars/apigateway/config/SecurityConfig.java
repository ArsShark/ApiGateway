package com.grechanikovars.apigateway.config;

import com.grechanikovars.apigateway.security.ProblemDetailSecurityHandler;
import com.grechanikovars.apigateway.security.ProblemDetailWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Spring Security setup of the Gateway: a stateless resource server that is closed by default.
 *
 * <p>The Gateway is an API for other programs, so there are no sessions, no CSRF protection (it
 * protects cookie sessions, and there are none), no login form, no HTTP Basic and no logout
 * page. Every request that no rule opens is denied: an anonymous caller gets 401, an
 * authenticated one 403, also for a path that does not exist. The rules that open the public
 * paths are added in the next commits.
 *
 * <p>Callers bring an access token of the Authentication Service in the header
 * {@code Authorization: Bearer ...}. It is verified here (see {@link JwtConfig}) and the claim
 * {@code role} becomes the authority. Both answers of the chain, 401 and 403, are written as
 * {@code ProblemDetail} by {@link ProblemDetailSecurityHandler}, also the 401 for a bad token,
 * so a missing, a wrong, an expired and an unverifiable token look the same.
 *
 * <p>The chain runs before the routing of Spring Cloud Gateway, so a request that is denied
 * here is never forwarded.
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
            .jwt(jwt -> jwt
                .jwtDecoder(jwtDecoder)
                .jwtAuthenticationConverter(jwtConverter)))
        .authorizeExchange(exchanges -> exchanges.anyExchange().denyAll())
        .build();
  }
}
