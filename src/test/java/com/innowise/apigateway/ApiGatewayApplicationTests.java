package com.innowise.apigateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.innowise.apigateway.integration.AbstractIntegrationTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveXForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.context.ApplicationContext;

/**
 * Checks that the application context starts with the default configuration and that the
 * headers of the proxied requests are set as intended.
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
