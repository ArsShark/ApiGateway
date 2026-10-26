package com.grechanikovars.apigateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Checks that the application context starts with the default configuration.
 */
@SpringBootTest
class ApiGatewayApplicationTests {

  @Autowired
  private ApplicationContext context;

  @Test
  void contextLoads() {
    assertAll(
        () -> assertNotNull(context),
        () -> assertNotNull(context.getBean(Clock.class)));
  }
}
