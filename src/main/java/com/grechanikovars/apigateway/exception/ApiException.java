package com.grechanikovars.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * An error that is answered to the client with a fixed status and a fixed text.
 *
 * <p>The detail is written for the client and is a constant, never built from a message of
 * another service, a value of the request or an address. It is also the message of the
 * exception, so it is safe in a log.
 */
public class ApiException extends RuntimeException {

  /** The detail of the 409 for a registration that conflicts with an existing account. */
  public static final String EMAIL_EXISTS = "An account with this email already exists";

  private final HttpStatus status;

  private ApiException(HttpStatus status, String detail) {
    super(detail);
    this.status = status;
  }

  /**
   * The email of the registration is taken: the profile exists already, or an account with this
   * login does.
   *
   * @return a 409 exception
   */
  public static ApiException emailExists() {
    return new ApiException(HttpStatus.CONFLICT, EMAIL_EXISTS);
  }

  /**
   * A service answered in a way the Gateway cannot use (an unexpected status or an empty answer).
   *
   * @return a 502 exception
   */
  public static ApiException badGateway() {
    return new ApiException(HttpStatus.BAD_GATEWAY, HttpStatus.BAD_GATEWAY.getReasonPhrase());
  }

  /**
   * Returns the status of the answer.
   *
   * @return the HTTP status
   */
  public HttpStatus getStatus() {
    return status;
  }

  /**
   * Returns the text for the client.
   *
   * @return the detail of the problem
   */
  public String getDetail() {
    return getMessage();
  }
}
