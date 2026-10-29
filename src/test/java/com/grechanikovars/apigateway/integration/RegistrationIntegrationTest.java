package com.grechanikovars.apigateway.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grechanikovars.apigateway.LogCapture;
import com.grechanikovars.apigateway.StubServer.RecordedRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The registration flow end to end through the Gateway: the HTTP contract, the two calls that it
 * makes to the services (stubs that record them), and the undoing when the second call fails.
 */
class RegistrationIntegrationTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "a-strong-password";
  private static final String BODY = """
      {"name":"Ann","surname":"Lee","birthDate":"1990-05-17","email":"Ann@Example.com",
       "password":"a-strong-password"}""";
  private static final String USER_JSON = """
      {"id":42,"name":"Ann","surname":"Lee","birthDate":"1990-05-17","email":"ann@example.com",
       "active":true,"createdAt":"2026-10-16T10:00:00Z","updatedAt":"2026-10-16T10:00:00Z"}""";

  @BeforeEach
  void happyPath() {
    userService().respondTo("POST", "/internal/users", 201, USER_JSON);
    userService().respondTo("DELETE", "/internal/users/42", 204, "");
    authService().respondTo("POST", "/internal/credentials", 201, "{\"id\":1,\"role\":\"USER\"}");
    authService().respondTo("DELETE", "/internal/credentials/by-user/42", 204, "");
  }

  @Test
  void aValidRegistrationCreatesTheProfileThenTheAccountAndAnswers201() throws Exception {
    WebTestClient.ResponseSpec response = register(BODY);

    String answer = response.expectStatus().isCreated()
        .expectBody(String.class).returnResult().getResponseBody();
    RecordedRequest profile = userService().lastRequest();
    RecordedRequest account = authService().lastRequest();
    assertAll(
        () -> new JsonPathExpectationsHelper("$.id").assertValue(answer, 42),
        () -> new JsonPathExpectationsHelper("$.name").assertValue(answer, "Ann"),
        () -> new JsonPathExpectationsHelper("$.email").assertValue(answer, "ann@example.com"),
        () -> new JsonPathExpectationsHelper("$.password").doesNotExist(answer),
        () -> assertEquals("POST", profile.method()),
        () -> assertEquals("/internal/users", profile.target()),
        () -> assertEquals(INTERNAL_KEY, profile.header("X-Internal-Key")),
        () -> new JsonPathExpectationsHelper("$.email").assertValue(profile.body(), "Ann@Example.com"),
        () -> new JsonPathExpectationsHelper("$.password").doesNotExist(profile.body()),
        () -> assertEquals("/internal/credentials", account.target()),
        () -> assertEquals(INTERNAL_KEY, account.header("X-Internal-Key")),
        () -> new JsonPathExpectationsHelper("$.login")
            .assertValue(account.body(), "ann@example.com"),
        () -> new JsonPathExpectationsHelper("$.password").assertValue(account.body(), PASSWORD),
        () -> new JsonPathExpectationsHelper("$.userId").assertValue(account.body(), 42),
        () -> assertEquals(1, userService().requests().size()),
        () -> assertEquals(1, authService().requests().size()));
  }

  @Test
  void anInvalidBodyIsA400WithTheFieldsAndNoValuesAndNoServiceIsCalled() {
    String invalid = """
        {"name":" ","surname":"Lee","birthDate":"2999-01-01","email":"nope","password":"short"}""";

    String answer = register(invalid).expectStatus().isBadRequest()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(String.class).returnResult().getResponseBody();

    assertAll(
        () -> new JsonPathExpectationsHelper("$.status").assertValue(answer, 400),
        () -> new JsonPathExpectationsHelper("$.errors[0].field").assertValue(answer, "birthDate"),
        () -> new JsonPathExpectationsHelper("$.errors[1].field").assertValue(answer, "email"),
        () -> new JsonPathExpectationsHelper("$.errors[2].field").assertValue(answer, "name"),
        () -> new JsonPathExpectationsHelper("$.errors[3].field").assertValue(answer, "password"),
        () -> assertFalse(answer.contains("nope")),
        () -> assertFalse(answer.contains("short")),
        () -> assertEquals(0, userService().requests().size()),
        () -> assertEquals(0, authService().requests().size()));
  }

  @Test
  void anUnreadableBodyIsA400AndNoServiceIsCalled() {
    register("{ this is not json").expectStatus().isBadRequest();

    assertAll(
        () -> assertEquals(0, userService().requests().size()),
        () -> assertEquals(0, authService().requests().size()));
  }

  @Test
  void aBodyThatIsNotJsonIsA415AndNoServiceIsCalled() {
    webTestClient.post().uri("/api/auth/register")
        .contentType(MediaType.TEXT_PLAIN).bodyValue(BODY)
        .exchange().expectStatus().isEqualTo(415);

    assertEquals(0, userService().requests().size());
  }

  @Test
  void aTakenEmailIsA409AndNothingIsUndone() {
    userService().respondTo("POST", "/internal/users", 409, "{}");

    String answer = register(BODY).expectStatus().isEqualTo(409)
        .expectBody(String.class).returnResult().getResponseBody();

    assertAll(
        () -> new JsonPathExpectationsHelper("$.detail")
            .assertValue(answer, "An account with this email already exists"),
        () -> assertEquals(1, userService().requests().size()),
        () -> assertEquals(0, authService().requests().size()));
  }

  @Test
  void aLoginThatIsTakenIsA409AndTheProfileIsRemoved() {
    authService().respondTo("POST", "/internal/credentials", 409, "{}");

    register(BODY).expectStatus().isEqualTo(409);

    assertUndone();
  }

  @Test
  void aFailureOfTheAuthenticationServiceIsA502AndTheProfileIsRemoved() {
    authService().respondTo("POST", "/internal/credentials", 500, "boom");

    String answer = register(BODY).expectStatus().isEqualTo(502)
        .expectBody(String.class).returnResult().getResponseBody();

    assertAll(
        () -> new JsonPathExpectationsHelper("$.detail").assertValue(answer, "Bad Gateway"),
        () -> assertFalse(answer.contains("boom")),
        () -> assertUndone());
  }

  @Test
  void anAccountCallThatGetsNoAnswerIsA504AndBothAreRemovedBecauseTheAccountMayExist() {
    authService().hangOn("POST", "/internal/credentials");

    String answer = register(BODY).expectStatus().isEqualTo(504)
        .expectBody(String.class).returnResult().getResponseBody();

    assertAll(
        () -> new JsonPathExpectationsHelper("$.detail").assertValue(answer, "Gateway Timeout"),
        () -> assertUndone());
  }

  @Test
  void theUndoingAcceptsThatThingsAreAlreadyGone() {
    authService().respondTo("POST", "/internal/credentials", 500, "{}");
    authService().respondTo("DELETE", "/internal/credentials/by-user/42", 404, "");
    userService().respondTo("DELETE", "/internal/users/42", 404, "");

    register(BODY).expectStatus().isEqualTo(502);

    assertUndone();
  }

  @Test
  void aFailedUndoingStillAnswersTheOriginalErrorAndTriesBothDeletions() {
    authService().respondTo("POST", "/internal/credentials", 409, "{}");
    authService().respondTo("DELETE", "/internal/credentials/by-user/42", 500, "");
    userService().respondTo("DELETE", "/internal/users/42", 500, "");

    register(BODY).expectStatus().isEqualTo(409);

    assertUndone();
  }

  @Test
  void aFailureOfTheUserServiceIsA502AndTheAuthenticationServiceIsNotCalled() {
    userService().respondTo("POST", "/internal/users", 500, "{}");

    register(BODY).expectStatus().isEqualTo(502);

    assertAll(
        () -> assertEquals(0, authService().requests().size()),
        () -> assertEquals(1, userService().requests().size()));
  }

  @Test
  void aRejectedInternalKeyIsA502NotA401() {
    userService().respondTo("POST", "/internal/users", 401, "{}");

    register(BODY).expectStatus().isEqualTo(502);
  }

  @Test
  void theRegistrationNeedsNoTokenAndIgnoresABadOne() {
    webTestClient.post().uri("/api/auth/register")
        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token")
        .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
        .exchange().expectStatus().isCreated();
  }

  @Test
  void noPasswordEmailOrKeyReachesTheLog() {
    try (LogCapture capture = LogCapture.attach()) {
      register(BODY).expectStatus().isCreated();
      authService().respondTo("POST", "/internal/credentials", 500, "{}");
      register(BODY).expectStatus().isEqualTo(502);

      assertAll(
          () -> assertTrue(capture.contains("User 42 registered"),
              "The run must log the success"),
          () -> assertTrue(capture.contains("undoing it"), "The run must log the undoing"),
          () -> assertFalse(capture.contains(PASSWORD)),
          () -> assertFalse(capture.contains(INTERNAL_KEY)),
          () -> assertFalse(capture.contains("ann@example.com")),
          () -> assertFalse(capture.contains("Ann@Example.com")));
    }
  }

  /** The account is deleted by the id of the profile, then the profile is deleted. */
  private void assertUndone() {
    List<RecordedRequest> authCalls = authService().requests();
    List<RecordedRequest> userCalls = userService().requests();
    assertAll(
        () -> assertEquals(2, authCalls.size()),
        () -> assertEquals("DELETE", authCalls.get(1).method()),
        () -> assertEquals("/internal/credentials/by-user/42", authCalls.get(1).target()),
        () -> assertEquals(INTERNAL_KEY, authCalls.get(1).header("X-Internal-Key")),
        () -> assertEquals(2, userCalls.size()),
        () -> assertEquals("DELETE", userCalls.get(1).method()),
        () -> assertEquals("/internal/users/42", userCalls.get(1).target()),
        () -> assertEquals(INTERNAL_KEY, userCalls.get(1).header("X-Internal-Key")));
  }

  private WebTestClient.ResponseSpec register(String body) {
    return webTestClient.post().uri("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
        .exchange();
  }
}
