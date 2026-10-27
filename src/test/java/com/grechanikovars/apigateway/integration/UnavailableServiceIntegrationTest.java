package com.grechanikovars.apigateway.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.grechanikovars.apigateway.JwksTestServer;
import com.grechanikovars.apigateway.TestClockConfiguration;
import com.grechanikovars.apigateway.TestKeys;
import com.grechanikovars.apigateway.TestTokens;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Checks what the caller gets when a service cannot be reached at all: the addresses of both
 * services point to a port where nobody listens, so the connection is refused. This is the
 * outage that Spring Cloud Gateway does not translate itself. The class has its own Spring
 * context, because it needs other addresses than the shared one.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@Import(TestClockConfiguration.class)
class UnavailableServiceIntegrationTest {

  private static final JwksTestServer KEY_SET_SERVER = JwksTestServer.start(TestKeys.jwksJson());
  private static final int CLOSED_PORT = freePort();

  @Autowired
  private WebTestClient webTestClient;

  @DynamicPropertySource
  static void addresses(DynamicPropertyRegistry registry) {
    String closed = String.format("http://127.0.0.1:%d", CLOSED_PORT);
    registry.add("app.security.jwks-uri", KEY_SET_SERVER::jwksUri);
    registry.add("app.services.user-uri", () -> closed);
    registry.add("app.services.auth-uri", () -> closed);
  }

  @Test
  void aRefusedConnectionToTheUserServiceIsA502Problem() {
    String body = new String(webTestClient.get().uri("/api/users/7")
        .header(HttpHeaders.AUTHORIZATION,
            String.format("Bearer %s", TestTokens.access().build()))
        .exchange()
        .expectStatus().isEqualTo(502)
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(502)
        .jsonPath("$.detail").isEqualTo("Bad Gateway")
        .returnResult().getResponseBody());

    assertAll(
        () -> assertFalse(body.contains("127.0.0.1")),
        () -> assertFalse(body.contains(String.valueOf(CLOSED_PORT))));
  }

  @Test
  void aRefusedConnectionToTheAuthenticationServiceIsA502Problem() {
    webTestClient.post().uri("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
        .exchange()
        .expectStatus().isEqualTo(502)
        .expectBody()
        .jsonPath("$.detail").isEqualTo("Bad Gateway");
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
