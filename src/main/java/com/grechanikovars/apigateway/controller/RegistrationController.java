package com.grechanikovars.apigateway.controller;

import com.grechanikovars.apigateway.dto.request.RegisterRequest;
import com.grechanikovars.apigateway.dto.response.RegisterResponse;
import com.grechanikovars.apigateway.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The public registration. It is answered by the Gateway itself and never forwarded to a
 * service: it needs two services and an undo, which a route cannot do.
 *
 * <p>The path is open for everybody (see {@code PublicPaths}).
 */
@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

  private final RegistrationService registrationService;

  /**
   * Creates the controller.
   *
   * @param registrationService runs the registration flow
   */
  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  /**
   * Registers a user: creates the profile and the account.
   *
   * @param request the data of the profile and the password
   * @return 201 with the new profile; 400 for an invalid body; 409 if the email is taken; 502
   *     or 504 if a service fails or does not answer. A failed registration leaves nothing behind
   */
  @PostMapping("/register")
  public Mono<ResponseEntity<RegisterResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    return registrationService.register(request)
        .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
  }
}
