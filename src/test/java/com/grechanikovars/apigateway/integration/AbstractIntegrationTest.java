package com.grechanikovars.apigateway.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Base class of the tests that start the whole application.
 *
 * <p>All subclasses share one Spring context, so they must not change it: no
 * {@code @MockitoBean}, {@code @TestPropertySource}, {@code @DirtiesContext} or extra
 * {@code @Import}. The requests go through the real filter chain (without a network port).
 */
@SpringBootTest
@AutoConfigureWebTestClient
public abstract class AbstractIntegrationTest {

  @Autowired
  protected WebTestClient webTestClient;
}
