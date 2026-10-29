package com.grechanikovars.apigateway.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.grechanikovars.apigateway.StubServer;
import com.grechanikovars.apigateway.exception.ApiException;
import java.time.Duration;
import java.time.LocalDate;
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
 * Tests {@link UserServiceClient} against a stub server: what it sends and how it turns every
 * status into an outcome.
 */
class UserServiceClientTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final String USER_JSON = """
      {"id":42,"name":"Ann","surname":"Lee","birthDate":"1990-05-17","email":"ann@example.com",
       "active":true,"createdAt":"2026-10-16T10:00:00Z","somethingNew":1}""";
  private static final InternalUserRequest REQUEST = new InternalUserRequest(
      "Ann", "Lee", LocalDate.of(1990, 5, 17), "Ann@Example.com");

  private static StubServer stub;
  private UserServiceClient client;

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
    client = new UserServiceClient(WebClient.builder()
        .baseUrl(stub.uri())
        .defaultHeader("X-Internal-Key", "a-key")
        .build());
  }

  @Test
  void aCreatedProfileIsReadAndUnknownFieldsAreIgnored() throws Exception {
    stub.respondTo("POST", "/internal/users", 201, USER_JSON);

    InternalUserResponse user = client.create(REQUEST).block(WAIT);

    String sent = stub.lastRequest().body();
    assertAll(
        () -> assertEquals(new InternalUserResponse(
            42L, "Ann", "Lee", LocalDate.of(1990, 5, 17), "ann@example.com"), user),
        () -> assertEquals("a-key", stub.lastRequest().header("X-Internal-Key")),
        () -> new JsonPathExpectationsHelper("$.email").assertValue(sent, "Ann@Example.com"),
        () -> new JsonPathExpectationsHelper("$.name").assertValue(sent, "Ann"),
        () -> new JsonPathExpectationsHelper("$.password").doesNotExist(sent));
  }

  @Test
  void aTakenEmailIsA409() {
    stub.respondTo("POST", "/internal/users", 409, "{}");

    StepVerifier.create(client.create(REQUEST))
        .expectErrorSatisfies(error -> assertAll(
            () -> assertInstanceOf(ApiException.class, error),
            () -> assertEquals(409, ((ApiException) error).getStatus().value())))
        .verify(WAIT);
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 400, 401, 403, 500})
  void anyOtherAnswerToACreationIsABadGateway(int status) {
    stub.respondTo("POST", "/internal/users", status, "{}");

    StepVerifier.create(client.create(REQUEST))
        .expectErrorSatisfies(error -> assertAll(
            () -> assertInstanceOf(ApiException.class, error),
            () -> assertEquals(502, ((ApiException) error).getStatus().value())))
        .verify(WAIT);
  }

  @ParameterizedTest
  @ValueSource(ints = {204, 404})
  void aDeletionAcceptsAlreadyGone(int status) {
    stub.respondTo("DELETE", "/internal/users/42", status, "");

    StepVerifier.create(client.delete(42L)).verifyComplete();

    assertAll(
        () -> assertEquals("DELETE", stub.lastRequest().method()),
        () -> assertEquals("/internal/users/42", stub.lastRequest().target()),
        () -> assertEquals("a-key", stub.lastRequest().header("X-Internal-Key")));
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 401, 500})
  void anyOtherAnswerToADeletionIsABadGateway(int status) {
    stub.respondTo("DELETE", "/internal/users/42", status, "");

    StepVerifier.create(client.delete(42L))
        .expectErrorSatisfies(error -> assertEquals(502, ((ApiException) error).getStatus().value()))
        .verify(WAIT);
  }
}
