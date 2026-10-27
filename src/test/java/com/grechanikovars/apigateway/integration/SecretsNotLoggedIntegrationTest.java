package com.grechanikovars.apigateway.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grechanikovars.apigateway.LogCapture;
import com.grechanikovars.apigateway.TestKeys;
import com.grechanikovars.apigateway.TestTokens;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * Checks end to end that no secret reaches the log: one run goes through successes, every kind of
 * refusal and a failure of the key set, then everything that was logged is searched for the
 * secrets of the run.
 *
 * <p>The test also requires the line that the run must write (the failure of the key set), so a
 * capture that sees nothing fails instead of passing.
 */
class SecretsNotLoggedIntegrationTest extends AbstractIntegrationTest {

  private static final String INTERNAL_KEY = "an-internal-key-that-must-stay-secret";
  private static final String BASIC_CREDENTIALS = "dXNlcjpwYXNzd29yZA==";

  @Test
  void noSecretOfTheRunReachesTheLog() {
    String userToken = TestTokens.access().build();
    String adminToken = TestTokens.admin().build();
    String garbage = "garbage.token.value";
    String expired = TestTokens.access()
        .issuedAt(TestTokens.ISSUED_AT.minus(Duration.ofMinutes(20)))
        .expiresAt(TestTokens.ISSUED_AT.minus(Duration.ofMinutes(5))).build();
    String unknownKey = TestTokens.access().keyId("an-unknown-key").build();
    String keySetAddress = keySetServer().jwksUri();

    try (LogCapture capture = LogCapture.attach()) {
      send(HttpMethod.GET, "/api/users/1", String.format("Bearer %s", userToken));
      send(HttpMethod.GET, "/api/users", String.format("Bearer %s", adminToken));
      send(HttpMethod.GET, "/internal/users", String.format("Bearer %s", userToken));
      send(HttpMethod.GET, "/api/users/1", String.format("Bearer %s", garbage));
      send(HttpMethod.GET, "/api/users/1", String.format("Bearer %s", expired));
      send(HttpMethod.GET, "/api/users/1", String.format("Bearer %s", unknownKey));
      send(HttpMethod.GET, "/api/users/1", String.format("Basic %s", BASIC_CREDENTIALS));
      send(HttpMethod.POST, "/api/auth/login", String.format("Bearer %s", expired));
      webTestClient.get().uri("/api/users/1")
          .header("X-Internal-Key", INTERNAL_KEY).exchange().expectBody().returnResult();
      sendWhileTheKeySetFails(userToken);

      List<String> secrets = new ArrayList<>(List.of(
          userToken, adminToken, garbage, expired, unknownKey, BASIC_CREDENTIALS,
          "user:password", INTERNAL_KEY, keySetAddress, "jwks.json"));
      for (String token : List.of(userToken, adminToken)) {
        secrets.add(token.split("\\.")[1]);
        secrets.add(token.split("\\.")[2]);
      }
      Stream<Executable> checks = secrets.stream().map(secret -> (Executable) () -> assertFalse(
          capture.contains(secret), "A secret of the run was logged"));
      assertAll(
          () -> assertTrue(capture.contains("A token could not be verified"),
              "The run must log the failure of the key set"),
          () -> assertAll(checks));
    }
  }

  /**
   * Makes the key set unavailable for one request: the cache of the keys has expired (the clock
   * moves two hours) and the server answers with an error. The answer is restored afterwards.
   */
  private void sendWhileTheKeySetFails(String token) {
    keySetServer().respondWith(500, "");
    clock.advance(Duration.ofHours(2));
    try {
      send(HttpMethod.GET, "/api/users/1", String.format("Bearer %s", token));
    } finally {
      keySetServer().respondWith(200, TestKeys.jwksJson());
    }
  }

  private void send(HttpMethod method, String path, String authorization) {
    webTestClient.method(method).uri(path)
        .header(HttpHeaders.AUTHORIZATION, authorization)
        .exchange()
        .expectBody().returnResult();
  }
}
