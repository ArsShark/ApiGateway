package com.grechanikovars.apigateway.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.grechanikovars.apigateway.StubServer;
import com.grechanikovars.apigateway.exception.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * Tests {@link AuthServiceClient} against a stub server: what it sends and how it turns every
 * status into an outcome.
 */
class AuthServiceClientTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final InternalCredentialRequest REQUEST =
      new InternalCredentialRequest("ann@example.com", "a-strong-password", 42L);

  private static StubServer stub;
  private AuthServiceClient client;

  @BeforeAll
  static void startStub() {
    stub = StubServer.start();
  }

  @AfterAll
  static void stopStub() {
    stub.close();
  }

  @BeforeEach
  void setUp() {
    stub.reset();
    client = new AuthServiceClient(WebClient.builder()
        .baseUrl(stub.uri())
        .defaultHeader("X-Internal-Key", "a-key")
        .build());
  }

  @Test
  void anAccountIsCreatedWithTheLoginThePasswordAndTheProfileId() throws Exception {
    stub.respondTo("POST", "/internal/credentials", 201, "{\"id\":1}");

    StepVerifier.create(client.createCredential(REQUEST)).verifyComplete();

    String sent = stub.lastRequest().body();
    assertAll(
        () -> assertEquals("a-key", stub.lastRequest().header("X-Internal-Key")),
        () -> new JsonPathExpectationsHelper("$.login").assertValue(sent, "ann@example.com"),
        () -> new JsonPathExpectationsHelper("$.password")
            .assertValue(sent, "a-strong-password"),
        () -> new JsonPathExpectationsHelper("$.userId").assertValue(sent, 42));
  }

  @Test
  void aTakenLoginIsA409() {
    stub.respondTo("POST", "/internal/credentials", 409, "{}");

    StepVerifier.create(client.createCredential(REQUEST))
        .expectErrorSatisfies(error -> assertAll(
            () -> assertInstanceOf(ApiException.class, error),
            () -> assertEquals(409, ((ApiException) error).getStatus().value())))
        .verify(WAIT);
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 400, 401, 403, 500})
  void anyOtherAnswerToACreationIsABadGateway(int status) {
    stub.respondTo("POST", "/internal/credentials", status, "{}");

    StepVerifier.create(client.createCredential(REQUEST))
        .expectErrorSatisfies(error -> assertAll(
            () -> assertInstanceOf(ApiException.class, error),
            () -> assertEquals(502, ((ApiException) error).getStatus().value()),
            () -> assertFalse(error.getMessage().contains("a-strong-password"))))
        .verify(WAIT);
  }

  @ParameterizedTest
  @ValueSource(ints = {204, 404})
  void aDeletionAcceptsAlreadyGone(int status) {
    stub.respondTo("DELETE", "/internal/credentials/by-user/42", status, "");

    StepVerifier.create(client.deleteByUserId(42L)).verifyComplete();

    assertAll(
        () -> assertEquals("DELETE", stub.lastRequest().method()),
        () -> assertEquals("/internal/credentials/by-user/42", stub.lastRequest().target()),
        () -> assertEquals("a-key", stub.lastRequest().header("X-Internal-Key")));
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 401, 500})
  void anyOtherAnswerToADeletionIsABadGateway(int status) {
    stub.respondTo("DELETE", "/internal/credentials/by-user/42", status, "");

    StepVerifier.create(client.deleteByUserId(42L))
        .expectErrorSatisfies(error -> assertEquals(502, ((ApiException) error).getStatus().value()))
        .verify(WAIT);
  }
}
