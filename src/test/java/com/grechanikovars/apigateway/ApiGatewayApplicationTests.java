package com.grechanikovars.apigateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.grechanikovars.apigateway.integration.AbstractIntegrationTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Checks that the application context starts with the default configuration.
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
}
