package com.grechanikovars.apigateway;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests the test helper {@link StubServer} itself. The tests of the Gateway trust it to answer,
 * to stay silent and to record, so its own behavior is pinned here: a helper that closes a
 * "hanging" connection at once would turn a timeout (504) into a dropped connection (502).
 */
class StubServerTest {

  private static final Duration SHORT = Duration.ofMillis(500);

  private static StubServer stub;
  private static HttpClient client;

  @BeforeAll
  static void start() {
    stub = StubServer.start();
    client = HttpClient.newHttpClient();
  }

  @AfterAll
  static void stop() {
    stub.close();
  }

  @AfterEach
  void reset() {
    stub.reset();
  }

  @Test
  void aRouteHangsInsteadOfClosingTheConnection() {
    stub.hangOn("POST", "/slow");

    assertThrows(HttpTimeoutException.class, () -> send("POST", "/slow", SHORT));
  }

  @Test
  void aHangingRouteDoesNotBlockTheOthers() throws Exception {
    stub.hangOn("POST", "/slow");
    stub.respondTo("GET", "/fast", 200, "fast");

    HttpResponse<String> response = send("GET", "/fast", SHORT);

    assertAll(
        () -> assertEquals(200, response.statusCode()),
        () -> assertEquals("fast", response.body()));
  }

  @Test
  void aRouteAnswerWinsOverTheDefaultAndOtherMethodsKeepTheDefault() throws Exception {
    stub.respondWith(201, "default");
    stub.respondTo("DELETE", "/things/1", 204, "");

    HttpResponse<String> deleted = send("DELETE", "/things/1", SHORT);
    HttpResponse<String> other = send("GET", "/things/1", SHORT);

    assertAll(
        () -> assertEquals(204, deleted.statusCode()),
        () -> assertEquals(201, other.statusCode()),
        () -> assertEquals("default", other.body()));
  }

  @Test
  void resetForgetsTheRequestsAndTheRoutes() throws Exception {
    stub.respondTo("GET", "/once", 418, "teapot");
    send("GET", "/once", SHORT);

    stub.reset();
    HttpResponse<String> afterReset = send("GET", "/once", SHORT);

    assertAll(
        () -> assertEquals(200, afterReset.statusCode()),
        () -> assertEquals(1, stub.requests().size()));
  }

  private static HttpResponse<String> send(String method, String path, Duration timeout)
      throws IOException, InterruptedException {
    HttpRequest request = HttpRequest
        .newBuilder(URI.create(String.format("%s%s", stub.uri(), path)))
        .method(method, HttpRequest.BodyPublishers.noBody())
        .timeout(timeout)
        .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
