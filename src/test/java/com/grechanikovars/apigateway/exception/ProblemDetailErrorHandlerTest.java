package com.grechanikovars.apigateway.exception;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grechanikovars.apigateway.security.ProblemDetailWriter;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

/**
 * Tests {@link ProblemDetailErrorHandler} without a Spring context: which status each kind of
 * error gets, and that the message of the exception (it holds the internal address of a
 * service) never reaches the answer.
 */
class ProblemDetailErrorHandlerTest {

  private final ProblemDetailErrorHandler handler =
      new ProblemDetailErrorHandler(new ProblemDetailWriter(ServerCodecConfigurer.create()));

  @Test
  void anExceptionWithAStatusKeepsItsStatusButNotItsMessage() throws Exception {
    MockServerWebExchange exchange = exchange();

    handler.handle(exchange, new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
        "Response took longer than timeout: PT1S")).block();

    MockServerHttpResponse response = exchange.getResponse();
    String body = response.getBodyAsString().block();
    assertAll(
        () -> assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode()),
        () -> assertTrue(MediaType.APPLICATION_PROBLEM_JSON
            .isCompatibleWith(response.getHeaders().getContentType())),
        () -> new JsonPathExpectationsHelper("$.detail").assertValue(body, "Gateway Timeout"),
        () -> assertFalse(body.contains("PT1S")));
  }

  @Test
  void aMissingRouteIsA404Problem() throws Exception {
    MockServerWebExchange exchange = exchange();

    handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

    String body = exchange.getResponse().getBodyAsString().block();
    assertAll(
        () -> assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode()),
        () -> new JsonPathExpectationsHelper("$.detail").assertValue(body, "Not Found"));
  }

  @Test
  void aRefusedConnectionIsA502EvenWhenWrapped() throws Exception {
    MockServerWebExchange exchange = exchange();
    Throwable error = new IllegalStateException("wrapper",
        new ConnectException("Connection refused: user-service/10.0.0.5:8082"));

    handler.handle(exchange, error).block();

    String body = exchange.getResponse().getBodyAsString().block();
    assertAll(
        () -> assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode()),
        () -> new JsonPathExpectationsHelper("$.detail").assertValue(body, "Bad Gateway"),
        () -> assertFalse(body.contains("10.0.0.5")),
        () -> assertFalse(body.contains("user-service")));
  }

  @Test
  void anUnknownHostIsA502() {
    MockServerWebExchange exchange = exchange();

    handler.handle(exchange, new UnknownHostException("user-service")).block();

    assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
  }

  @Test
  void aConnectTimeoutIsA504NotA502() {
    MockServerWebExchange exchange = exchange();

    handler.handle(exchange, new ConnectTimeoutException("connection timed out")).block();

    assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
  }

  @Test
  void anyOtherErrorIsA500WithAConstantDetail() throws Exception {
    MockServerWebExchange exchange = exchange();

    handler.handle(exchange, new IllegalStateException("a secret detail")).block();

    String body = exchange.getResponse().getBodyAsString().block();
    assertAll(
        () -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode()),
        () -> new JsonPathExpectationsHelper("$.detail")
            .assertValue(body, "Internal Server Error"),
        () -> assertFalse(body.contains("a secret detail")));
  }

  @Test
  void aResponseThatIsAlreadySentIsLeftAlone() {
    MockServerWebExchange exchange = exchange();
    exchange.getResponse().setComplete().block();
    IllegalStateException error = new IllegalStateException("late");

    StepVerifier.create(handler.handle(exchange, error))
        .expectErrorSatisfies(thrown -> assertSame(error, thrown))
        .verify();
  }

  @Test
  void anApiExceptionKeepsItsStatusAndItsConstantDetail() throws Exception {
    MockServerWebExchange exchange = exchange();

    handler.handle(exchange, ApiException.emailExists()).block();

    String body = exchange.getResponse().getBodyAsString().block();
    assertAll(
        () -> assertEquals(HttpStatus.CONFLICT, exchange.getResponse().getStatusCode()),
        () -> new JsonPathExpectationsHelper("$.detail")
            .assertValue(body, "An account with this email already exists"));
  }

  @Test
  void aBadGatewayExceptionIsA502() throws Exception {
    MockServerWebExchange exchange = exchange();

    handler.handle(exchange, ApiException.badGateway()).block();

    String body = exchange.getResponse().getBodyAsString().block();
    assertAll(
        () -> assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode()),
        () -> new JsonPathExpectationsHelper("$.detail").assertValue(body, "Bad Gateway"));
  }

  @Test
  void aTimeoutOfAnAnswerInsideAFailedRequestIsA504() {
    MockServerWebExchange exchange = exchange();
    Throwable error = new WebClientRequestException(ReadTimeoutException.INSTANCE,
        HttpMethod.POST, URI.create("http://auth.internal:8081/internal/credentials"),
        new HttpHeaders());

    handler.handle(exchange, error).block();

    assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
  }

  @Test
  void aFailedRequestWithARefusedConnectionIsA502() throws Exception {
    MockServerWebExchange exchange = exchange();
    Throwable error = new WebClientRequestException(
        new ConnectException("Connection refused: auth.internal/10.0.0.6:8081"),
        HttpMethod.POST, URI.create("http://auth.internal:8081/internal/credentials"),
        new HttpHeaders());

    handler.handle(exchange, error).block();

    String body = exchange.getResponse().getBodyAsString().block();
    assertAll(
        () -> assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode()),
        () -> assertFalse(body.contains("auth.internal")),
        () -> assertFalse(body.contains("10.0.0.6")));
  }

  @Test
  void anyOtherFailedRequestIsA502() {
    MockServerWebExchange exchange = exchange();
    Throwable error = new WebClientRequestException(new IOException("reset"),
        HttpMethod.GET, URI.create("http://user.internal:8082/internal/users"),
        new HttpHeaders());

    handler.handle(exchange, error).block();

    assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/1"));
  }
}
