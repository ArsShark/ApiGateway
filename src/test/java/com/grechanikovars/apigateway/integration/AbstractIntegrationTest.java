package com.grechanikovars.apigateway.integration;

import com.grechanikovars.apigateway.JwksTestServer;
import com.grechanikovars.apigateway.MutableClock;
import com.grechanikovars.apigateway.TestClockConfiguration;
import com.grechanikovars.apigateway.TestKeys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Base class of the tests that start the whole application.
 *
 * <p>All subclasses share one Spring context, so they must not change it: no
 * {@code @MockitoBean}, {@code @TestPropertySource}, {@code @DirtiesContext} or extra
 * {@code @Import}. The requests go through the real filter chain (without a network port). The
 * tokens are verified against a real HTTP server that plays the key set endpoint of the
 * Authentication Service; it is shared by all tests and never stopped. The clock is movable and
 * goes back to its start before every test.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@Import(TestClockConfiguration.class)
public abstract class AbstractIntegrationTest {

  private static final JwksTestServer KEY_SET_SERVER = JwksTestServer.start(TestKeys.jwksJson());

  @Autowired
  protected WebTestClient webTestClient;

  @Autowired
  protected MutableClock clock;

  /**
   * Returns the key set server that the shared context reads its keys from. A test that changes
   * its answer must restore it in a {@code finally} part.
   *
   * @return the server
   */
  protected static JwksTestServer keySetServer() {
    return KEY_SET_SERVER;
  }

  @DynamicPropertySource
  static void keySet(DynamicPropertyRegistry registry) {
    registry.add("app.security.jwks-uri", KEY_SET_SERVER::jwksUri);
  }

  @BeforeEach
  void resetClock() {
    clock.set(TestClockConfiguration.START);
  }
}
