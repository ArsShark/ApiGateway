package com.grechanikovars.apigateway.security;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Removes the header {@code X-Internal-Key} from every request that comes from outside.
 *
 * <p>The header is the credential of the calls between the services (for example the creation of
 * an account in the Authentication Service). The Gateway sends it itself during the registration,
 * with its own client. A caller from the internet must never be able to send it through the
 * Gateway to a service, so it is dropped before anything else sees the request, also before the
 * security chain.
 *
 * <p>The value is never read or logged.
 */
@Component
public class InternalKeyRemovalFilter implements WebFilter, Ordered {

  /** The name of the header; header names are case-insensitive. */
  public static final String HEADER = "X-Internal-Key";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!exchange.getRequest().getHeaders().containsHeader(HEADER)) {
      return chain.filter(exchange);
    }
    ServerWebExchange stripped = exchange.mutate()
        .request(request -> request.headers(headers -> headers.remove(HEADER)))
        .build();
    return chain.filter(stripped);
  }

  /** The filter runs before every other filter, the security chain included. */
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
