package com.grechanikovars.apigateway.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.JsonPathExpectationsHelper;

/**
 * Tests {@link ProblemDetailSecurityHandler} and {@link ProblemDetailWriter} without a Spring
 * context, with the default codecs of Spring.
 */
class ProblemDetailSecurityHandlerTest {

  private final ProblemDetailSecurityHandler handler =
      new ProblemDetailSecurityHandler(new ProblemDetailWriter(ServerCodecConfigurer.create()));

  @Test
  void anUnauthenticatedRequestGets401WithAConstantProblem() throws Exception {
    MockServerWebExchange exchange = exchange();

    handler.commence(exchange, new BadCredentialsException("the secret reason")).block();

    MockServerHttpResponse response = exchange.getResponse();
    String body = response.getBodyAsString().block();
    assertAll(
        () -> assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode()),
        () -> assertTrue(MediaType.APPLICATION_PROBLEM_JSON
            .isCompatibleWith(response.getHeaders().getContentType())),
        () -> new JsonPathExpectationsHelper("$.status").assertValue(body, 401),
        () -> new JsonPathExpectationsHelper("$.title").assertValue(body, "Unauthorized"),
        () -> new JsonPathExpectationsHelper("$.detail")
            .assertValue(body, "Authentication is required"),
        () -> assertFalse(body.contains("the secret reason")));
  }

  @Test
  void aForbiddenRequestGets403WithAConstantProblem() throws Exception {
    MockServerWebExchange exchange = exchange();

    handler.handle(exchange, new AccessDeniedException("the secret reason")).block();

    MockServerHttpResponse response = exchange.getResponse();
    String body = response.getBodyAsString().block();
    assertAll(
        () -> assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode()),
        () -> new JsonPathExpectationsHelper("$.status").assertValue(body, 403),
        () -> new JsonPathExpectationsHelper("$.title").assertValue(body, "Forbidden"),
        () -> new JsonPathExpectationsHelper("$.detail").assertValue(body, "Access is denied"),
        () -> assertFalse(body.contains("the secret reason")));
  }

  @Test
  void withoutAWriterTheResponseKeepsTheBareStatus() {
    ServerCodecConfigurer noCodecs = mock(ServerCodecConfigurer.class);
    when(noCodecs.getWriters()).thenReturn(List.of());
    ProblemDetailSecurityHandler failing =
        new ProblemDetailSecurityHandler(new ProblemDetailWriter(noCodecs));
    MockServerWebExchange exchange = exchange();

    failing.commence(exchange, new BadCredentialsException("x")).block();

    MockServerHttpResponse response = exchange.getResponse();
    assertAll(
        () -> assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode()),
        () -> assertEquals("", response.getBodyAsString().block()));
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/1"));
  }
}
