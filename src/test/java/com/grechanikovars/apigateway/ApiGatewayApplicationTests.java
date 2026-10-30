package com.grechanikovars.apigateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveXForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
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

  /**
   * Pins the trap of Spring Cloud Gateway 5: the X-Forwarded-* headers are sent only when
   * {@code trusted-proxies} is set. Without it the filter that removes them is registered
   * instead, and the services would build links to their own internal address.
   */
  @Test
  void theXForwardedHeadersAreSwitchedOnAndTheStandardForwardedHeaderIsNot() {
    assertAll(
        () -> assertEquals(1, context.getBeansOfType(XForwardedHeadersFilter.class).size()),
        () -> assertEquals(0, context.getBeansOfType(RemoveXForwardedHeadersFilter.class).size()),
        () -> assertEquals(0, context.getBeansOfType(ForwardedHeadersFilter.class).size()),
        () -> assertEquals(1, context.getBeansOfType(RemoveForwardedHeadersFilter.class).size()));
  }
}
