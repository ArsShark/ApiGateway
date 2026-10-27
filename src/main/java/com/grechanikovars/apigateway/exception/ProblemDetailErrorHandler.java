package com.grechanikovars.apigateway.exception;

import com.grechanikovars.apigateway.security.ProblemDetailWriter;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

/**
 * Answers the errors of the routing as RFC 9457 problems, in the same format as every other
 * error of the Gateway.
 *
 * <p>Spring Cloud Gateway turns only the timeout of an answer into a status (504). A refused or
 * dropped connection reaches the error handling as a raw exception, and Spring Boot would answer
 * it with its own JSON and a 500. Here the error is mapped to what happened:
 *
 * <ul>
 *   <li>an exception with a status ({@code ResponseStatusException}: no route, wrong method,
 *       the 504 of the timeout): that status;
 *   <li>the connection to the service timed out: 504;
 *   <li>the service could not be reached or closed the connection (refused, unknown host,
 *       dropped): 502;
 *   <li>anything else is a bug: 500.
 * </ul>
 *
 * <p>The detail is the standard reason phrase of the status. The message of the exception is
 * never sent: it contains the internal address of the service. The answers of a service itself,
 * also its errors, are passed on unchanged and never reach this handler.
 */
@Component
@Order(-2)
public class ProblemDetailErrorHandler implements WebExceptionHandler {

  private static final Logger LOGGER = LogManager.getLogger(ProblemDetailErrorHandler.class);

  private static final int MAX_CAUSE_DEPTH = 10;

  private final ProblemDetailWriter writer;

  /**
   * Creates the handler.
   *
   * @param writer writes the problem into the response
   */
  public ProblemDetailErrorHandler(ProblemDetailWriter writer) {
    this.writer = writer;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(error);
    }
    HttpStatus status = statusOf(error);
    if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
      LOGGER.error("Unexpected error while handling a request", error);
    } else if (status.is5xxServerError()) {
      LOGGER.warn("A service did not answer properly: {} {}", status.value(),
          error.getClass().getSimpleName());
    }
    return writer.write(exchange, status, status.getReasonPhrase());
  }

  private static HttpStatus statusOf(Throwable error) {
    if (error instanceof ResponseStatusException responseStatus) {
      HttpStatus status = HttpStatus.resolve(responseStatus.getStatusCode().value());
      return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
    Throwable current = error;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof ConnectTimeoutException) {
        return HttpStatus.GATEWAY_TIMEOUT;
      }
      if (current instanceof ConnectException
          || current instanceof UnknownHostException
          || current instanceof PrematureCloseException) {
        return HttpStatus.BAD_GATEWAY;
      }
      current = current.getCause();
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }
}
