package com.grechanikovars.apigateway.client;

import com.grechanikovars.apigateway.config.InternalClientsConfig;
import com.grechanikovars.apigateway.exception.ApiException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Calls the internal endpoints of the Authentication Service for the registration flow.
 *
 * <p>It follows the same rules as {@link UserServiceClient}: a status becomes a success, a
 * conflict or a {@code badGateway}, a call without an answer is a plain error of the
 * {@code Mono}, and the password is only in the body of the request, never in a log.
 */
@Component
public class AuthServiceClient {

  private static final Logger LOGGER = LogManager.getLogger(AuthServiceClient.class);

  private final WebClient webClient;

  /**
   * Creates the client.
   *
   * @param webClient the client of the Authentication Service that sends the internal key
   */
  public AuthServiceClient(@Qualifier(InternalClientsConfig.AUTH_SERVICE_WEB_CLIENT)
      WebClient webClient) {
    this.webClient = webClient;
  }

  /**
   * Creates the account of a profile.
   *
   * @param request the login, the password and the id of the profile
   * @return completes when the account exists; an error with status 409 if the login is taken or
   *     the profile has an account, and {@code badGateway} for any other unexpected answer
   */
  public Mono<Void> createCredential(InternalCredentialRequest request) {
    return webClient.post()
        .uri("/internal/credentials")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchangeToMono(response -> {
          HttpStatusCode status = response.statusCode();
          if (status.value() == 201) {
            return response.releaseBody();
          }
          return response.releaseBody().then(Mono.error(failureOf(status)));
        });
  }

  /**
   * Deletes the account of a profile. A profile without an account is not an error: the call
   * undoes a registration and may be repeated.
   *
   * @param userId the id of the profile in the User Service
   * @return completes when the account is gone; {@code badGateway} for an unexpected answer
   */
  public Mono<Void> deleteByUserId(long userId) {
    return webClient.delete()
        .uri("/internal/credentials/by-user/{userId}", userId)
        .exchangeToMono(response -> {
          int status = response.statusCode().value();
          if (status == 204 || status == 404) {
            return response.releaseBody();
          }
          LOGGER.error("The deletion of an account was answered with {}", status);
          return response.releaseBody().then(Mono.error(ApiException.badGateway()));
        });
  }

  private static ApiException failureOf(HttpStatusCode status) {
    if (status.value() == 409) {
      return ApiException.emailExists();
    }
    LOGGER.error("The creation of an account was answered with {}", status.value());
    return ApiException.badGateway();
  }
}
