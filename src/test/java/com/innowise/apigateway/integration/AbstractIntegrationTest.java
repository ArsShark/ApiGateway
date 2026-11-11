package com.innowise.apigateway.integration;

import com.innowise.apigateway.JwksTestServer;
import com.innowise.apigateway.MutableClock;
import com.innowise.apigateway.StubServer;
import com.innowise.apigateway.TestClockConfiguration;
import com.innowise.apigateway.TestKeys;
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
 * Authentication Service. The User Service, the Authentication Service and the Order Service are
 * played by three stub servers that record what the Gateway sends them (see
 * {@link #userService()}, {@link #authService()} and {@link #orderService()}); they are forgotten
 * before every test. All the servers are shared by
 * all tests and never stopped. The clock is movable and goes back to its start before every
 * test. The Gateway waits one second for an answer of a service, so a test of a slow service
 * is quick.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@Import(TestClockConfiguration.class)
public abstract class AbstractIntegrationTest {

  private static final JwksTestServer KEY_SET_SERVER = JwksTestServer.start(TestKeys.jwksJson());
  private static final StubServer USER_SERVICE = StubServer.start();
  private static final StubServer AUTH_SERVICE = StubServer.start();
  private static final StubServer ORDER_SERVICE = StubServer.start();

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

  /**
   * Returns the stub that plays the User Service.
   *
   * @return the server
   */
  protected static StubServer userService() {
    return USER_SERVICE;
  }

  /**
   * Returns the stub that plays the Authentication Service.
   *
   * @return the server
   */
  protected static StubServer authService() {
    return AUTH_SERVICE;
  }

  /**
   * Returns the stub that plays the Order Service.
   *
   * @return the server
   */
  protected static StubServer orderService() {
    return ORDER_SERVICE;
  }

  @DynamicPropertySource
  static void servers(DynamicPropertyRegistry registry) {
    registry.add("app.security.jwks-uri", KEY_SET_SERVER::jwksUri);
    registry.add("app.services.user-uri", USER_SERVICE::uri);
    registry.add("app.services.auth-uri", AUTH_SERVICE::uri);
    registry.add("app.services.order-uri", ORDER_SERVICE::uri);
    registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "1s");
    registry.add("app.internal.response-timeout", () -> "1s");
  }

  @BeforeEach
  void resetState() {
    clock.set(TestClockConfiguration.START);
    USER_SERVICE.reset();
    AUTH_SERVICE.reset();
    ORDER_SERVICE.reset();
  }
}
