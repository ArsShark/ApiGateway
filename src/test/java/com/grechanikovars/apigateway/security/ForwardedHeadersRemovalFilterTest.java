package com.grechanikovars.apigateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Tests {@link ForwardedHeadersRemovalFilter} without a Spring context.
 */
class ForwardedHeadersRemovalFilterTest {

  private final ForwardedHeadersRemovalFilter filter = new ForwardedHeadersRemovalFilter();
  private final AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
  private final WebFilterChain chain = exchange -> {
    seen.set(exchange);
    return Mono.empty();
  };

  @ParameterizedTest
  @ValueSource(strings = {
      "Forwarded", "X-Forwarded-For", "x-forwarded-host", "X-FORWARDED-PORT", "X-Forwarded-Proto",
      "X-Forwarded-Prefix", "X-Forwarded-Ssl"})
  void everyForwardedHeaderIsRemovedWhateverTheCase(String name) {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/users/1").header(name, "evil.example"));

    filter.filter(exchange, chain).block();

    assertFalse(seen.get().getRequest().getHeaders().containsHeader(name));
  }

  @Test
  void theOtherHeadersAreKept() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
        .get("/api/users/1")
        .header("X-Forwarded-Host", "evil.example")
        .header(HttpHeaders.AUTHORIZATION, "Bearer a-token")
        .header(HttpHeaders.HOST, "gateway.example"));

    filter.filter(exchange, chain).block();

    HttpHeaders headers = seen.get().getRequest().getHeaders();
    assertEquals("Bearer a-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
    assertEquals("gateway.example", headers.getFirst(HttpHeaders.HOST));
  }

  @Test
  void aRequestWithoutForwardedHeadersIsPassedOnUnchanged() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/1"));

    filter.filter(exchange, chain).block();

    assertSame(exchange, seen.get());
  }

  @Test
  void theFilterRunsRightAfterTheInternalKeyFilter() {
    assertEquals(new InternalKeyRemovalFilter().getOrder() + 1, filter.getOrder());
    assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, filter.getOrder());
  }
}
