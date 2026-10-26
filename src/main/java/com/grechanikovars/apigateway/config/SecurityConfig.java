package com.grechanikovars.apigateway.config;

import com.grechanikovars.apigateway.security.ProblemDetailSecurityHandler;
import com.grechanikovars.apigateway.security.ProblemDetailWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Spring Security setup of the Gateway: a stateless chain that is closed by default.
 *
 * <p>The Gateway is an API for other programs, so there are no sessions, no CSRF protection (it
 * protects cookie sessions, and there are none), no login form, no HTTP Basic and no logout
 * page. Every request that no rule opens is denied: an anonymous caller gets 401, an
 * authenticated one 403, also for a path that does not exist. The rules that open the public
 * paths and the verification of the token are added in the next commits.
 *
 * <p>Both answers of the chain are written as {@code ProblemDetail} by
 * {@link ProblemDetailSecurityHandler}. The chain runs before the routing of Spring Cloud
 * Gateway, so a request that is denied here is never forwarded.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

  /**
   * Builds the one security filter chain of the Gateway.
   *
   * @param http the builder provided by Spring Security
   * @param problemWriter writes the 401 and 403 answers
   * @return the chain that handles every request
   */
  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
      ProblemDetailWriter problemWriter) {
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
        .authorizeExchange(exchanges -> exchanges.anyExchange().denyAll())
        .build();
  }
}
