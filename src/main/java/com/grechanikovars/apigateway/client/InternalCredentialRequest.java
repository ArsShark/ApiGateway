package com.grechanikovars.apigateway.client;

/**
 * The body of {@code POST /internal/credentials} of the Authentication Service: the account to
 * create.
 *
 * <p>{@link #toString()} is overridden: the generated one would print the password.
 *
 * @param login the login, which is the email of the profile
 * @param password the raw password
 * @param userId the id of the profile in the User Service
 */
public record InternalCredentialRequest(String login, String password, Long userId) {

  /**
   * Describes the request without any of its values.
   *
   * @return a fixed text
   */
  @Override
  public String toString() {
    return "InternalCredentialRequest[***]";
  }
}
