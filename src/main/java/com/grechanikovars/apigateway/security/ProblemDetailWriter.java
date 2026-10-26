package com.grechanikovars.apigateway.security;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes an error as an RFC 9457 {@link ProblemDetail} ({@code application/problem+json}) for
 * code that runs in the filter chain, where {@code @RestControllerAdvice} does not apply.
 *
 * <p>The body is written by the message writers of the application, the same ones that write
 * the responses of controllers, so the format is the same everywhere. If no writer can write
 * it, the response gets the bare status and an empty body: a failure of the error handling
 * never turns into a 200.
 */
@Component
public class ProblemDetailWriter {

  private static final Logger LOGGER = LogManager.getLogger(ProblemDetailWriter.class);

  private static final ResolvableType PROBLEM_DETAIL = ResolvableType.forClass(ProblemDetail.class);

  private final ServerCodecConfigurer codecs;

  /**
   * Creates the writer.
   *
   * @param codecs the codecs of the application
   */
  public ProblemDetailWriter(ServerCodecConfigurer codecs) {
    this.codecs = codecs;
  }

  /**
   * Answers the exchange with a problem.
   *
   * @param exchange the exchange to answer
   * @param status the HTTP status
   * @param detail the text of the problem; it must be a constant, never a message of an
   *     exception or a value of the request
   * @return completes when the response is written; empty if the response was already sent
   */
  public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String detail) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();
    }
    response.setStatusCode(status);
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    for (HttpMessageWriter<?> candidate : codecs.getWriters()) {
      if (candidate.canWrite(PROBLEM_DETAIL, MediaType.APPLICATION_PROBLEM_JSON)) {
        @SuppressWarnings("unchecked")
        HttpMessageWriter<ProblemDetail> writer = (HttpMessageWriter<ProblemDetail>) candidate;
        return writer.write(Mono.just(problem), PROBLEM_DETAIL,
            MediaType.APPLICATION_PROBLEM_JSON, response, Map.of());
      }
    }
    LOGGER.error("No message writer can write a problem, sending the bare status {}",
        status.value());
    return response.setComplete();
  }
}
