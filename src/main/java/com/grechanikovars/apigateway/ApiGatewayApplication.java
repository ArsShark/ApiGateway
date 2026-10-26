package com.grechanikovars.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the API Gateway.
 *
 * <p>The Gateway is the only public entry point of the system: it routes requests to the
 * business services, checks the JWT and runs the registration flow.
 */
@SpringBootApplication
public class ApiGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }

}
