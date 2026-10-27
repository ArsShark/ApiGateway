package com.grechanikovars.apigateway.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Tests {@link InternalKeyRemovalFilter} without a Spring context.
 */
class InternalKeyRemovalFilterTest {

  private final InternalKeyRemovalFilter filter = new InternalKeyRemovalFilter();
  private final AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
  private final WebFilterChain chain = exchange -> {
    seen.set(exchange);
    return Mono.empty();
  };

  @Test
  void theHeaderIsRemovedWhateverTheCaseAndHoweverManyTimesItIsSent() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
        .get("/api/users/1")
        .header("X-Internal-Key", "first")
        .header("x-internal-key", "second")
        .header("X-INTERNAL-KEY", "third"));

    filter.filter(exchange, chain).block();

    assertFalse(seen.get().getRequest().getHeaders().containsHeader("X-Internal-Key"));
  }

  @Test
  void theOtherHeadersAndTheRequestAreKept() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
        .post("/api/auth/login")
        .header("X-Internal-Key", "secret")
        .header(HttpHeaders.AUTHORIZATION, "Bearer a-token")
        .header(HttpHeaders.ACCEPT, "application/json"));

    filter.filter(exchange, chain).block();

    HttpHeaders headers = seen.get().getRequest().getHeaders();
    assertAll(
        () -> assertEquals("Bearer a-token", headers.getFirst(HttpHeaders.AUTHORIZATION)),
        () -> assertEquals("application/json", headers.getFirst(HttpHeaders.ACCEPT)),
        () -> assertEquals("/api/auth/login", seen.get().getRequest().getPath().value()),
        () -> assertEquals("POST", seen.get().getRequest().getMethod().name()));
  }

  @Test
  void aRequestWithoutTheHeaderIsPassedOnUnchanged() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/1"));

    filter.filter(exchange, chain).block();

    assertSame(exchange, seen.get());
  }

  @Test
  void theFilterRunsBeforeEveryOtherFilter() {
    assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder());
  }
}
