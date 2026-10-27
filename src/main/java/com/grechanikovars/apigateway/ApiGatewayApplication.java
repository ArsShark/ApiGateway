package com.grechanikovars.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration;

/**
 * Entry point of the API Gateway.
 *
 * <p>The Gateway is the only public entry point of the system: it routes requests to the
 * business services, checks the JWT and runs the registration flow.
 *
 * <p>{@link ReactiveUserDetailsServiceAutoConfiguration} is excluded: without a user store of
 * its own Spring Boot creates a user with a random password and prints it to the log. The
 * Gateway has no user store, callers prove who they are with a token. The exclusion is by
 * class, so the compiler checks the name.
 */
@SpringBootApplication(exclude = ReactiveUserDetailsServiceAutoConfiguration.class)
public class ApiGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }

}
