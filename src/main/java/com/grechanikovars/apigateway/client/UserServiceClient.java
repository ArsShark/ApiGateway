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
 * Calls the internal endpoints of the User Service for the registration flow.
 *
 * <p>The status of every answer is turned into the outcome the flow needs: success, a conflict
 * that the client must hear about, or a {@link ApiException#badGateway()} for everything the
 * Gateway cannot use. A call that does not get an answer at all (refused, timeout) is an error
 * of the returned {@code Mono} that is not an {@code ApiException}; the error handler maps it.
 * The body and the headers of an unexpected answer are never read or logged: only its status.
 */
@Component
public class UserServiceClient {

  private static final Logger LOGGER = LogManager.getLogger(UserServiceClient.class);

  private final WebClient webClient;

  /**
   * Creates the client.
   *
   * @param webClient the client of the User Service that sends the internal key
   */
  public UserServiceClient(@Qualifier(InternalClientsConfig.USER_SERVICE_WEB_CLIENT)
      WebClient webClient) {
    this.webClient = webClient;
  }

  /**
   * Creates a profile.
   *
   * @param request the data of the profile
   * @return the created profile; an error with status 409 if the email is taken, and
   *     {@code badGateway} for any other unexpected answer
   */
  public Mono<InternalUserResponse> create(InternalUserRequest request) {
    return webClient.post()
        .uri("/internal/users")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchangeToMono(response -> {
          HttpStatusCode status = response.statusCode();
          if (status.value() == 201) {
            return response.bodyToMono(InternalUserResponse.class);
          }
          return response.releaseBody().then(Mono.error(failureOf(status)));
        });
  }

  /**
   * Deletes a profile. A profile that does not exist is not an error: the call undoes a
   * registration and may be repeated.
   *
   * @param userId the id of the profile
   * @return completes when the profile is gone; {@code badGateway} for an unexpected answer
   */
  public Mono<Void> delete(long userId) {
    return webClient.delete()
        .uri("/internal/users/{id}", userId)
        .exchangeToMono(response -> {
          int status = response.statusCode().value();
          if (status == 204 || status == 404) {
            return response.releaseBody();
          }
          LOGGER.error("The deletion of a profile was answered with {}", status);
          return response.releaseBody().then(Mono.error(ApiException.badGateway()));
        });
  }

  private static ApiException failureOf(HttpStatusCode status) {
    if (status.value() == 409) {
      return ApiException.emailExists();
    }
    LOGGER.error("The creation of a profile was answered with {}", status.value());
    return ApiException.badGateway();
  }
}
