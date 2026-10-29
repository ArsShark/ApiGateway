package com.grechanikovars.apigateway.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.grechanikovars.apigateway.client.AuthServiceClient;
import com.grechanikovars.apigateway.client.InternalCredentialRequest;
import com.grechanikovars.apigateway.client.InternalUserRequest;
import com.grechanikovars.apigateway.client.InternalUserResponse;
import com.grechanikovars.apigateway.client.UserServiceClient;
import com.grechanikovars.apigateway.dto.request.RegisterRequest;
import com.grechanikovars.apigateway.dto.response.RegisterResponse;
import com.grechanikovars.apigateway.exception.ApiException;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests of {@link RegistrationService}: plain Mockito, no Spring, no network. The two
 * clients are mocks, so the tests decide what each step answers and check what the service does
 * about it, above all the undoing.
 *
 * <p>The profile id (42) is different from every other number on purpose.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final LocalDate BORN = LocalDate.of(1990, 5, 17);
  private static final long USER_ID = 42L;
  private static final RegisterRequest REQUEST =
      new RegisterRequest("Ann", "Lee", BORN, "Ann@Example.com", "a-strong-password");
  private static final InternalUserResponse USER =
      new InternalUserResponse(USER_ID, "Ann", "Lee", BORN, "ann@example.com");

  @Mock
  private UserServiceClient users;

  @Mock
  private AuthServiceClient accounts;

  private RegistrationService service;

  @BeforeEach
  void setUp() {
    service = new RegistrationService(users, accounts);
  }

  @Test
  void bothStepsSucceedAndNothingIsUndone() {
    when(users.create(any())).thenReturn(Mono.just(USER));
    when(accounts.createCredential(any())).thenReturn(Mono.empty());

    StepVerifier.create(service.register(REQUEST))
        .expectNext(new RegisterResponse(USER_ID, "Ann", "Lee", BORN, "ann@example.com"))
        .verifyComplete();

    ArgumentCaptor<InternalUserRequest> profile =
        ArgumentCaptor.forClass(InternalUserRequest.class);
    ArgumentCaptor<InternalCredentialRequest> account =
        ArgumentCaptor.forClass(InternalCredentialRequest.class);
    verify(users).create(profile.capture());
    verify(accounts).createCredential(account.capture());
    assertAll(
        () -> assertEquals("Ann@Example.com", profile.getValue().email()),
        () -> assertEquals("ann@example.com", account.getValue().login()),
        () -> assertEquals("a-strong-password", account.getValue().password()),
        () -> assertEquals(USER_ID, account.getValue().userId()));
    verify(accounts, never()).deleteByUserId(anyLong());
    verify(users, never()).delete(anyLong());
  }

  @Test
  void aRefusedAccountUndoesTheProfileInTheRightOrder() {
    ApiException conflict = ApiException.emailExists();
    when(users.create(any())).thenReturn(Mono.just(USER));
    when(accounts.createCredential(any())).thenReturn(Mono.error(conflict));
    when(accounts.deleteByUserId(USER_ID)).thenReturn(Mono.empty());
    when(users.delete(USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(service.register(REQUEST))
        .expectErrorSatisfies(error -> assertSame(conflict, error))
        .verify(WAIT);

    InOrder inOrder = inOrder(accounts, users);
    inOrder.verify(accounts).deleteByUserId(USER_ID);
    inOrder.verify(users).delete(USER_ID);
  }

  @Test
  void anAccountCallWithoutAnAnswerIsUndoneToo() {
    RuntimeException lost = new RuntimeException("the answer never came");
    when(users.create(any())).thenReturn(Mono.just(USER));
    when(accounts.createCredential(any())).thenReturn(Mono.error(lost));
    when(accounts.deleteByUserId(USER_ID)).thenReturn(Mono.empty());
    when(users.delete(USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(service.register(REQUEST))
        .expectErrorSatisfies(error -> assertSame(lost, error))
        .verify(WAIT);

    verify(accounts).deleteByUserId(USER_ID);
    verify(users).delete(USER_ID);
  }

  @Test
  void aFailedDeletionOfTheAccountDoesNotStopTheDeletionOfTheProfile() {
    ApiException original = ApiException.badGateway();
    when(users.create(any())).thenReturn(Mono.just(USER));
    when(accounts.createCredential(any())).thenReturn(Mono.error(original));
    when(accounts.deleteByUserId(USER_ID)).thenReturn(Mono.error(new IllegalStateException("x")));
    when(users.delete(USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(service.register(REQUEST))
        .expectErrorSatisfies(error -> assertSame(original, error))
        .verify(WAIT);

    verify(users).delete(USER_ID);
  }

  @Test
  void aFailedDeletionOfTheProfileStillReportsTheOriginalError() {
    ApiException original = ApiException.emailExists();
    when(users.create(any())).thenReturn(Mono.just(USER));
    when(accounts.createCredential(any())).thenReturn(Mono.error(original));
    when(accounts.deleteByUserId(USER_ID)).thenReturn(Mono.empty());
    when(users.delete(USER_ID)).thenReturn(Mono.error(new IllegalStateException("x")));

    StepVerifier.create(service.register(REQUEST))
        .expectErrorSatisfies(error -> assertSame(original, error))
        .verify(WAIT);
  }

  @Test
  void aProfileThatCannotBeCreatedStartsNoAccountAndUndoesNothing() {
    ApiException conflict = ApiException.emailExists();
    when(users.create(any())).thenReturn(Mono.error(conflict));

    StepVerifier.create(service.register(REQUEST))
        .expectErrorSatisfies(error -> assertSame(conflict, error))
        .verify(WAIT);

    verifyNoInteractions(accounts);
    verify(users, never()).delete(anyLong());
  }

  @Test
  void anEmptyAnswerOfTheUserServiceIsABadGateway() {
    when(users.create(any())).thenReturn(Mono.empty());

    StepVerifier.create(service.register(REQUEST))
        .expectErrorSatisfies(error -> assertAll(
            () -> assertInstanceOf(ApiException.class, error),
            () -> assertEquals(502, ((ApiException) error).getStatus().value())))
        .verify(WAIT);

    verifyNoInteractions(accounts);
  }

  @Test
  void aClientThatDisconnectsDoesNotInterruptTheUndoing() {
    when(users.create(any())).thenReturn(Mono.just(USER));
    when(accounts.createCredential(any())).thenReturn(
        Mono.delay(Duration.ofMillis(200)).then(Mono.<Void>error(new RuntimeException("late"))));
    when(accounts.deleteByUserId(USER_ID)).thenReturn(Mono.empty());
    when(users.delete(USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(service.register(REQUEST)).thenCancel().verify(WAIT);

    verify(accounts, timeout(3_000)).deleteByUserId(USER_ID);
    verify(users, timeout(3_000)).delete(USER_ID);
  }
}
