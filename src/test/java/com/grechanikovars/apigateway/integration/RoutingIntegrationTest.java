package com.grechanikovars.apigateway.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grechanikovars.apigateway.StubServer.RecordedRequest;
import com.grechanikovars.apigateway.TestTokens;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Checks the routes end to end: what the Gateway forwards to the services, what it passes back,
 * what it never forwards, and how it answers when a service is slow. The services are stubs that
 * record every request.
 */
class RoutingIntegrationTest extends AbstractIntegrationTest {

  private static final String USER_BODY = "{\"id\":7}";

  private static String bearer() {
    return String.format("Bearer %s", TestTokens.access().build());
  }

  @Test
  void aUserRequestIsForwardedWithItsMethodPathQueryAndToken() {
    userService().respondWith(200, USER_BODY);
    String authorization = bearer();

    webTestClient.get().uri("/api/users?name=ann&page=1")
        .header(HttpHeaders.AUTHORIZATION, authorization)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class).isEqualTo(USER_BODY);

    RecordedRequest request = userService().lastRequest();
    assertAll(
        () -> assertEquals(1, userService().requests().size()),
        () -> assertEquals("GET", request.method()),
        () -> assertEquals("/api/users?name=ann&page=1", request.target()),
        () -> assertEquals(authorization, request.header(HttpHeaders.AUTHORIZATION)),
        () -> assertEquals(0, authService().requests().size()));
  }

  @Test
  void aBodyAndItsContentTypeAreForwarded() {
    userService().respondWith(201, "{\"id\":3}");
    String json = """
        {"number":"4111111111111111","holder":"ANN LEE"}""";

    webTestClient.post().uri("/api/users/7/cards")
        .header(HttpHeaders.AUTHORIZATION, bearer())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(json)
        .exchange()
        .expectStatus().isCreated();

    RecordedRequest request = userService().lastRequest();
    assertAll(
        () -> assertEquals("POST", request.method()),
        () -> assertEquals("/api/users/7/cards", request.target()),
        () -> assertEquals(json, request.body()),
        () -> assertTrue(request.header(HttpHeaders.CONTENT_TYPE).startsWith("application/json")));
  }

  @Test
  void theCardsPathsGoToTheUserServiceToo() {
    userService().respondWith(204, "");

    webTestClient.delete().uri("/api/cards/1")
        .header(HttpHeaders.AUTHORIZATION, bearer())
        .exchange()
        .expectStatus().isNoContent();

    assertAll(
        () -> assertEquals("DELETE", userService().lastRequest().method()),
        () -> assertEquals("/api/cards/1", userService().lastRequest().target()));
  }

  @Test
  void theStatusTheHeadersAndTheBodyOfAServiceArePassedBackUnchanged() {
    userService().respondWith(409, "{\"status\":409,\"detail\":\"Email already exists\"}",
        Map.of("Location", "http://example.test/api/users/9", "X-Request-Id", "abc"));

    webTestClient.post().uri("/api/users")
        .header(HttpHeaders.AUTHORIZATION, bearer())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectHeader().valueEquals("Location", "http://example.test/api/users/9")
        .expectHeader().valueEquals("X-Request-Id", "abc")
        .expectBody(String.class).isEqualTo("{\"status\":409,\"detail\":\"Email already exists\"}");
  }

  @Test
  void anErrorOfAServiceIsNotTranslatedByTheGateway() {
    userService().respondWith(500, "boom");

    webTestClient.get().uri("/api/users/7")
        .header(HttpHeaders.AUTHORIZATION, bearer())
        .exchange()
        .expectStatus().is5xxServerError()
        .expectBody(String.class).isEqualTo("boom");
  }

  @Test
  void loginRefreshAndValidateGoToTheAuthenticationService() {
    authService().respondWith(200, "{\"accessToken\":\"x\"}");
    String credentials = "{\"login\":\"ann@example.com\",\"password\":\"a-password-1\"}";

    webTestClient.post().uri("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON).bodyValue(credentials)
        .exchange().expectStatus().isOk();
    webTestClient.post().uri("/api/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"refreshToken\":\"r\"}")
        .exchange().expectStatus().isOk();
    webTestClient.post().uri("/api/auth/validate")
        .header(HttpHeaders.AUTHORIZATION, bearer())
        .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"token\":\"t\"}")
        .exchange().expectStatus().isOk();

    var requests = authService().requests();
    assertAll(
        () -> assertEquals(3, requests.size()),
        () -> assertEquals("/api/auth/login", requests.get(0).target()),
        () -> assertEquals(credentials, requests.get(0).body()),
        () -> assertEquals("/api/auth/refresh", requests.get(1).target()),
        () -> assertEquals("/api/auth/validate", requests.get(2).target()),
        () -> assertEquals(0, userService().requests().size()));
  }

  @Test
  void theRegistrationIsNeverProxiedToTheAuthenticationService() {
    webTestClient.post().uri("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
        .exchange();

    assertAll(
        () -> assertEquals(0, authService().requests().size()),
        () -> assertEquals(0, userService().requests().size()));
  }

  @Test
  void aPathThatNoRouteOwnsReachesNoService() {
    String token = TestTokens.access().build();

    webTestClient.post().uri("/api/auth/credentials")
        .header(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token))
        .exchange().expectStatus().isForbidden();
    webTestClient.get().uri("/api/auth/login").exchange().expectStatus().isUnauthorized();
    webTestClient.post().uri("/internal/credentials")
        .header(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token))
        .exchange().expectStatus().isForbidden();
    webTestClient.get().uri("/.well-known/jwks.json").exchange().expectStatus().isUnauthorized();

    assertAll(
        () -> assertEquals(0, authService().requests().size()),
        () -> assertEquals(0, userService().requests().size()));
  }

  @Test
  void theInternalKeyOfAClientNeverReachesAService() {
    webTestClient.get().uri("/api/users/7")
        .header(HttpHeaders.AUTHORIZATION, bearer())
        .header("X-Internal-Key", "the-key-of-an-attacker")
        .exchange().expectStatus().isOk();
    webTestClient.post().uri("/api/auth/login")
        .header("x-internal-key", "the-key-of-an-attacker")
        .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
        .exchange().expectStatus().isOk();

    assertAll(
        () -> assertFalse(userService().lastRequest().hasHeader("X-Internal-Key")),
        () -> assertFalse(authService().lastRequest().hasHeader("X-Internal-Key")));
  }

  @Test
  void forwardedHeadersOfAClientNeverReachAService() {
    webTestClient.get().uri("/api/users/7")
        .header(HttpHeaders.AUTHORIZATION, bearer())
        .header("X-Forwarded-Host", "evil.example")
        .header("X-Forwarded-For", "6.6.6.6")
        .header("X-Forwarded-Proto", "https")
        .header("X-Forwarded-Prefix", "/evil")
        .header("Forwarded", "for=6.6.6.6;host=evil.example")
        .exchange().expectStatus().isOk();

    RecordedRequest request = userService().lastRequest();
    String everything = request.headers().toString();
    assertAll(
        () -> assertFalse(everything.contains("evil")),
        () -> assertFalse(everything.contains("6.6.6.6")),
        () -> assertFalse(request.hasHeader("Forwarded")),
        () -> assertFalse(request.hasHeader("X-Forwarded-Prefix")));
  }

  @Test
  void aSlowServiceGetsA504ProblemWithoutItsAddress() {
    userService().hang();

    WebTestClient.BodyContentSpec body = webTestClient.get().uri("/api/users/7")
        .header(HttpHeaders.AUTHORIZATION, bearer())
        .exchange()
        .expectStatus().isEqualTo(504)
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(504)
        .jsonPath("$.detail").isEqualTo("Gateway Timeout");

    String text = new String(body.returnResult().getResponseBody());
    assertFalse(text.contains(userService().uri()));
  }
}
