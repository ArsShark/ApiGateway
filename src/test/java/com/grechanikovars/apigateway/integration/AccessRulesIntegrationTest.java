package com.grechanikovars.apigateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grechanikovars.apigateway.TestTokens;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The access matrix of the Gateway: which request is refused before it reaches any service, for
 * an anonymous caller, a user and an admin. "Open" means that the Gateway lets the request
 * pass; what happens next (a route, an answer of a service) is not the subject of this test, so
 * it only checks that the status is neither 401 nor 403.
 */
class AccessRulesIntegrationTest extends AbstractIntegrationTest {

  private static final String OPEN = "open";
  private static final String UNAUTHORIZED = "401";
  private static final String FORBIDDEN = "403";

  static Stream<Arguments> matrix() {
    return Stream.of(
        // anonymous: only the three public requests pass
        Arguments.of("POST", "/api/auth/login", "anonymous", OPEN),
        Arguments.of("POST", "/api/auth/refresh", "anonymous", OPEN),
        Arguments.of("POST", "/api/auth/register", "anonymous", OPEN),
        Arguments.of("GET", "/api/auth/login", "anonymous", UNAUTHORIZED),
        Arguments.of("POST", "/api/auth/validate", "anonymous", UNAUTHORIZED),
        Arguments.of("POST", "/api/auth/credentials", "anonymous", UNAUTHORIZED),
        Arguments.of("GET", "/api/users/1", "anonymous", UNAUTHORIZED),
        Arguments.of("POST", "/api/users", "anonymous", UNAUTHORIZED),
        Arguments.of("GET", "/api/cards/1", "anonymous", UNAUTHORIZED),
        Arguments.of("GET", "/internal/users", "anonymous", UNAUTHORIZED),
        Arguments.of("POST", "/internal/credentials", "anonymous", UNAUTHORIZED),
        Arguments.of("GET", "/.well-known/jwks.json", "anonymous", UNAUTHORIZED),
        Arguments.of("GET", "/api/orders/1", "anonymous", UNAUTHORIZED),
        Arguments.of("GET", "/actuator/health", "anonymous", UNAUTHORIZED),
        // user
        Arguments.of("GET", "/api/users/7", "user", OPEN),
        Arguments.of("POST", "/api/users/7/cards", "user", OPEN),
        Arguments.of("DELETE", "/api/cards/1", "user", OPEN),
        Arguments.of("POST", "/api/auth/validate", "user", OPEN),
        Arguments.of("POST", "/api/auth/login", "user", OPEN),
        Arguments.of("GET", "/api/auth/login", "user", FORBIDDEN),
        Arguments.of("POST", "/internal/credentials", "user", FORBIDDEN),
        Arguments.of("GET", "/internal/users", "user", FORBIDDEN),
        Arguments.of("GET", "/api/orders/1", "user", FORBIDDEN),
        Arguments.of("GET", "/.well-known/jwks.json", "user", FORBIDDEN),
        // admin: the same open paths, and nothing more
        Arguments.of("GET", "/api/users", "admin", OPEN),
        Arguments.of("PATCH", "/api/users/7/activate", "admin", OPEN),
        Arguments.of("GET", "/api/cards", "admin", OPEN),
        Arguments.of("POST", "/api/auth/validate", "admin", OPEN),
        Arguments.of("POST", "/internal/users", "admin", FORBIDDEN),
        Arguments.of("GET", "/api/orders/1", "admin", FORBIDDEN));
  }

  static Stream<Arguments> badTokens() {
    return Stream.of(
        Arguments.of("garbage", (Supplier<String>) () -> "not.a.token"),
        Arguments.of("expired", (Supplier<String>) () -> TestTokens.access()
            .issuedAt(TestTokens.ISSUED_AT.minus(Duration.ofMinutes(20)))
            .expiresAt(TestTokens.ISSUED_AT.minus(Duration.ofMinutes(5))).build()),
        Arguments.of("another issuer",
            (Supplier<String>) () -> TestTokens.access().issuer("somebody-else").build()));
  }

  @ParameterizedTest(name = "{0} {1} as {2}: {3}")
  @MethodSource("matrix")
  void theRequestIsRefusedOrLetThrough(String method, String path, String who, String expected) {
    WebTestClient.RequestBodySpec request =
        webTestClient.method(HttpMethod.valueOf(method)).uri(path);
    String token = tokenOf(who);
    if (token != null) {
      request.header(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token));
    }

    int status = request.exchange().returnResult(String.class).getStatus().value();

    switch (expected) {
      case UNAUTHORIZED -> assertEquals(401, status);
      case FORBIDDEN -> assertEquals(403, status);
      default -> assertTrue(status != 401 && status != 403,
          String.format("The Gateway refused the request with %d", status));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUPERUSER", "admin", "user", "ADMIN USER"})
  void aRoleThatIsNotExactlyAdminOrUserOpensNothing(String role) {
    String token = TestTokens.access().role(role).build();

    webTestClient.get().uri("/api/users/1")
        .header(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token))
        .exchange()
        .expectStatus().isForbidden();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("badTokens")
  void aBadTokenDoesNotSpoilARequestThatNeedsNoToken(String name, Supplier<String> token) {
    int status = webTestClient.post().uri("/api/auth/login")
        .header(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token.get()))
        .exchange().returnResult(String.class).getStatus().value();

    assertTrue(status != 401 && status != 403,
        String.format("The Gateway refused the request with %d", status));
  }

  private static String tokenOf(String who) {
    return switch (who) {
      case "user" -> TestTokens.access().build();
      case "admin" -> TestTokens.admin().build();
      default -> null;
    };
  }
}
