package com.grechanikovars.apigateway.service;

import com.grechanikovars.apigateway.client.AuthServiceClient;
import com.grechanikovars.apigateway.client.InternalCredentialRequest;
import com.grechanikovars.apigateway.client.InternalUserRequest;
import com.grechanikovars.apigateway.client.InternalUserResponse;
import com.grechanikovars.apigateway.client.UserServiceClient;
import com.grechanikovars.apigateway.dto.request.RegisterRequest;
import com.grechanikovars.apigateway.dto.response.RegisterResponse;
import com.grechanikovars.apigateway.exception.ApiException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * The registration flow: creates the profile in the User Service, then the account in the
 * Authentication Service, and undoes the first step if the second one fails.
 *
 * <p>Each service has its own database, so there is no transaction around the two steps. The
 * Gateway keeps the two consistent by compensation:
 *
 * <ol>
 *   <li>the profile is created first, because the account needs its id;
 *   <li>the account is created with the email of the profile (the form in which the User Service
 *       stored it) as the login;
 *   <li>if the account cannot be created, the account is deleted by the id of the profile and then
 *       the profile is deleted. The account is deleted even when the Authentication Service
 *       refused it or did not answer: if the answer was only lost, the account may exist, and an
 *       account without a profile would hold the email for ever. Both deletions accept "already
 *       gone", so they are safe to repeat. A failed deletion is logged and does not stop the
 *       other one.
 * </ol>
 *
 * <p>The whole flow runs independently of the client: if the client closes the connection after
 * the profile was created, the flow still finishes or undoes its work. A crash of the Gateway
 * between the two steps cannot be undone by this code and leaves a profile without an account
 * (a known limit, documented in the README).
 *
 * <p>The password and the email are never logged: only the id of the profile.
 */
@Service
public class RegistrationService {

  private static final Logger LOGGER = LogManager.getLogger(RegistrationService.class);

  private final UserServiceClient users;
  private final AuthServiceClient accounts;

  /**
   * Creates the service.
   *
   * @param users the client of the User Service
   * @param accounts the client of the Authentication Service
   */
  public RegistrationService(UserServiceClient users, AuthServiceClient accounts) {
    this.users = users;
    this.accounts = accounts;
  }

  /**
   * Registers a user.
   *
   * @param request the validated data of the registration
   * @return the new profile; an {@link ApiException} with 409 if the email is taken, 502 if a
   *     service answers unexpectedly; the errors of a call without an answer are mapped by the
   *     error handler (502 or 504)
   */
  public Mono<RegisterResponse> register(RegisterRequest request) {
    return Mono.defer(() -> {
      // The flow is subscribed here and not by the caller, so a client that disconnects cannot
      // cancel it in the middle (between the two steps or during the undoing).
      Sinks.One<RegisterResponse> result = Sinks.one();
      flow(request).subscribe(
          result::tryEmitValue,
          result::tryEmitError,
          result::tryEmitEmpty);
      return result.asMono();
    });
  }

  private Mono<RegisterResponse> flow(RegisterRequest request) {
    InternalUserRequest profile = new InternalUserRequest(
        request.name(), request.surname(), request.birthDate(), request.email());
    return users.create(profile)
        .switchIfEmpty(Mono.error(ApiException::badGateway))
        .flatMap(user -> accounts
            .createCredential(
                new InternalCredentialRequest(user.email(), request.password(), user.id()))
            .thenReturn(toResponse(user))
            .doOnSuccess(response -> LOGGER.info("User {} registered", user.id()))
            .onErrorResume(error -> undo(user.id(), error).then(Mono.error(error))));
  }

  /** Undoes the registration of a profile: first the account, then the profile. */
  private Mono<Void> undo(Long userId, Throwable cause) {
    LOGGER.warn("Registration of user {} failed ({}), undoing it", userId,
        cause.getClass().getSimpleName());
    return accounts.deleteByUserId(userId)
        .onErrorResume(error -> incomplete("account", userId, error))
        .then(Mono.defer(() -> users.delete(userId)
            .onErrorResume(error -> incomplete("profile", userId, error))));
  }

  private static Mono<Void> incomplete(String what, Long userId, Throwable error) {
    LOGGER.error("Undoing the registration of user {} is incomplete: the {} must be removed by"
        + " hand ({})", userId, what, error.getClass().getSimpleName());
    return Mono.empty();
  }

  private static RegisterResponse toResponse(InternalUserResponse user) {
    return new RegisterResponse(
        user.id(), user.name(), user.surname(), user.birthDate(), user.email());
  }
}
