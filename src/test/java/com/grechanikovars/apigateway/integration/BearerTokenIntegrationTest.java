package com.grechanikovars.apigateway.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.grechanikovars.apigateway.TestKeys;
import com.grechanikovars.apigateway.TestTokens;
import java.security.KeyPair;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Checks the token check through the whole filter chain. No rule opens any path yet, so a valid
 * token is authenticated and then denied (403), and every bad token gets exactly the answer of a
 * request without a token (401).
 */
class BearerTokenIntegrationTest extends AbstractIntegrationTest {

  private static final String PATH = "/api/users/1";

  static Stream<Arguments> badTokens() {
    KeyPair other = TestKeys.generateRsa(2048);
    return Stream.of(
        Arguments.of("garbage", (Supplier<String>) () -> "not.a.token"),
        Arguments.of("expired", (Supplier<String>) () -> TestTokens.access()
            .issuedAt(TestTokens.ISSUED_AT.minus(Duration.ofMinutes(20)))
            .expiresAt(TestTokens.ISSUED_AT.minus(Duration.ofMinutes(5))).build()),
        Arguments.of("another issuer",
            (Supplier<String>) () -> TestTokens.access().issuer("somebody-else").build()),
        Arguments.of("a refresh token",
            (Supplier<String>) () -> TestTokens.access().tokenType("refresh").build()),
        Arguments.of("no token type",
            (Supplier<String>) () -> TestTokens.access().tokenType(null).build()),
        Arguments.of("an unknown key id",
            (Supplier<String>) () -> TestTokens.access().keyId("an-unknown-key").build()),
        Arguments.of("another key with the known key id",
            (Supplier<String>) () -> TestTokens.access().signedWith(other).build()),
        Arguments.of("no expiry",
            (Supplier<String>) () -> TestTokens.access().expiresAt(null).build()));
  }

  @Test
  void aValidAdminTokenIsAuthenticatedButNothingIsOpenYet() {
    withToken(TestTokens.admin().build()).expectStatus().isForbidden()
        .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(403)
        .jsonPath("$.detail").isEqualTo("Access is denied");
  }

  @Test
  void aValidUserTokenIsDeniedToo() {
    withToken(TestTokens.access().build()).expectStatus().isForbidden();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("badTokens")
  void aBadTokenGetsTheSameAnswerAsNoToken(String name, Supplier<String> token) {
    String withoutToken = bodyOf(webTestClient.get().uri(PATH).exchange());

    WebTestClient.ResponseSpec response = withToken(token.get());

    response.expectStatus().isUnauthorized()
        .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
    assertEquals(withoutToken, bodyOf(withToken(token.get())));
  }

  @Test
  void aHeaderThatIsNotABearerTokenIsTreatedAsNoToken() {
    String withoutToken = bodyOf(webTestClient.get().uri(PATH).exchange());

    WebTestClient.ResponseSpec response = webTestClient.get().uri(PATH)
        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNzd29yZA==").exchange();

    response.expectStatus().isUnauthorized();
    assertEquals(withoutToken, bodyOf(webTestClient.get().uri(PATH)
        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNzd29yZA==").exchange()));
  }

  @Test
  void aTokenThatExpiresWhileTheGatewayRunsIsRejected() {
    String token = TestTokens.access().build();
    withToken(token).expectStatus().isForbidden();

    clock.advance(Duration.ofHours(1));

    withToken(token).expectStatus().isUnauthorized();
  }

  private WebTestClient.ResponseSpec withToken(String token) {
    return webTestClient.get().uri(PATH)
        .header(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token))
        .exchange();
  }

  private static String bodyOf(WebTestClient.ResponseSpec response) {
    return response.expectBody(String.class).returnResult().getResponseBody();
  }
}
