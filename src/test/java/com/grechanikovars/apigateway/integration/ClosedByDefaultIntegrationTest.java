package com.grechanikovars.apigateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;

/**
 * Checks that the chain is closed by default: no rule opens any path yet, so every request of
 * an anonymous caller is a 401 with the problem body, whether the path exists or not.
 */
class ClosedByDefaultIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private ApplicationContext context;

  @ParameterizedTest
  @ValueSource(strings = {
      "/", "/api/users/1", "/api/auth/login", "/internal/users", "/.well-known/jwks.json",
      "/actuator/health"})
  void anAnonymousCallerGetsA401OnEveryPath(String path) {
    webTestClient.get().uri(path).exchange()
        .expectStatus().isUnauthorized()
        .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT", "DELETE"})
  void everyMethodIsClosedToo(String method) {
    webTestClient.method(HttpMethod.valueOf(method)).uri("/api/users/1").exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void theAnswerIsAProblemDetailWithConstantTexts() {
    webTestClient.get().uri("/api/users/1").exchange()
        .expectStatus().isUnauthorized()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(401)
        .jsonPath("$.title").isEqualTo("Unauthorized")
        .jsonPath("$.detail").isEqualTo("Authentication is required")
        .jsonPath("$.properties").doesNotExist();
  }

  @Test
  void noDefaultUserIsCreated() {
    assertEquals(0, context.getBeanNamesForType(ReactiveUserDetailsService.class).length);
  }
}
