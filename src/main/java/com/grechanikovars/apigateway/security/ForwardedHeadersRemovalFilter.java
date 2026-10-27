package com.grechanikovars.apigateway.security;

import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Removes the {@code Forwarded} and {@code X-Forwarded-*} headers from every request that comes
 * from outside.
 *
 * <p>The services build absolute links (the {@code Location} of a created resource) from these
 * headers and trust them, because they are behind the Gateway. Spring Cloud Gateway adds its own
 * values, but it appends them to what the client sent. A client that sends
 * {@code X-Forwarded-Host: evil.example} would get links to its own host in the answers. The
 * Gateway is the one that knows the real host, so it starts from empty headers.
 *
 * <p>When the system runs behind an Ingress (Kubernetes), the Ingress is the one that must be
 * trusted instead; that is decided in the Kubernetes task.
 */
@Component
public class ForwardedHeadersRemovalFilter implements WebFilter, Ordered {

  private static final List<String> HEADERS = List.of(
      "Forwarded",
      "X-Forwarded-For",
      "X-Forwarded-Host",
      "X-Forwarded-Port",
      "X-Forwarded-Proto",
      "X-Forwarded-Prefix",
      "X-Forwarded-Ssl");

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    boolean present = HEADERS.stream()
        .anyMatch(header -> exchange.getRequest().getHeaders().containsHeader(header));
    if (!present) {
      return chain.filter(exchange);
    }
    ServerWebExchange stripped = exchange.mutate()
        .request(request -> request.headers(headers -> HEADERS.forEach(headers::remove)))
        .build();
    return chain.filter(stripped);
  }

  /** The filter runs right after {@link InternalKeyRemovalFilter}, before everything else. */
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }
}
