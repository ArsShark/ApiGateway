package com.grechanikovars.apigateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * The routes of the Gateway: which public path goes to which service.
 *
 * <p>The routes are written in Java, not in YAML, so the paths are checked by the compiler and
 * the addresses come from {@link ServicesProperties}. The security rules decide who may call a
 * path (see {@link SecurityConfig}); a route only decides where it goes. A path without a
 * route is a 404.
 *
 * <ul>
 *   <li>{@code /api/users/**} and {@code /api/cards/**} go to the User Service;
 *   <li>{@code POST /api/auth/login}, {@code /refresh} and {@code /validate} go to the
 *       Authentication Service. The paths are listed one by one, not as {@code /api/auth/**}, so
 *       nothing else of that service is reachable by accident;
 *   <li>{@code POST /api/auth/register} has no route: the Gateway answers it itself (it creates
 *       the profile and the account).
 * </ul>
 *
 * <p>The internal paths ({@code /internal/**}) and the key set of the Authentication Service are
 * never routed.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServicesProperties.class)
public class RoutesConfig {

  /**
   * Creates the routes.
   *
   * @param builder the builder of Spring Cloud Gateway
   * @param services the addresses of the services
   * @return the routes
   */
  @Bean
  public RouteLocator routes(RouteLocatorBuilder builder, ServicesProperties services) {
    return builder.routes()
        .route("user-service", route -> route
            .path("/api/users/**", "/api/cards/**")
            .uri(services.userUri()))
        .route("authentication-service", route -> route
            .method(HttpMethod.POST)
            .and()
            .path("/api/auth/login", "/api/auth/refresh", "/api/auth/validate")
            .uri(services.authUri()))
        .build();
  }
}
