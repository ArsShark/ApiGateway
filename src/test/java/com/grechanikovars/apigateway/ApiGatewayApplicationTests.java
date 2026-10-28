package com.grechanikovars.apigateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grechanikovars.apigateway.client.AuthServiceClient;
import com.grechanikovars.apigateway.client.UserServiceClient;
import com.grechanikovars.apigateway.config.InternalClientsConfig;
import com.grechanikovars.apigateway.integration.AbstractIntegrationTest;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Checks that the application context starts with the default configuration and that the beans
 * of the registration flow are wired as intended.
 */
class ApiGatewayApplicationTests extends AbstractIntegrationTest {

  @Autowired
  private ApplicationContext context;

  @Test
  void contextLoads() {
    assertAll(
        () -> assertNotNull(context),
        () -> assertNotNull(context.getBean(Clock.class)));
  }

  @Test
  void theInternalClientsAndTheirWebClientsAreSeparateBeansWithDifferentNames() {
    WebClient userWebClient =
        context.getBean(InternalClientsConfig.USER_SERVICE_WEB_CLIENT, WebClient.class);
    WebClient authWebClient =
        context.getBean(InternalClientsConfig.AUTH_SERVICE_WEB_CLIENT, WebClient.class);
    List<String> webClientNames = List.of(context.getBeanNamesForType(WebClient.class));

    assertAll(
        () -> assertNotSame(userWebClient, authWebClient),
        () -> assertTrue(webClientNames.contains(InternalClientsConfig.USER_SERVICE_WEB_CLIENT)),
        () -> assertTrue(webClientNames.contains(InternalClientsConfig.AUTH_SERVICE_WEB_CLIENT)),
        () -> assertNotNull(context.getBean(UserServiceClient.class)),
        () -> assertNotNull(context.getBean(AuthServiceClient.class)));
  }
}
