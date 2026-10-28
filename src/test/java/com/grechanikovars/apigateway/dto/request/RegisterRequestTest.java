package com.grechanikovars.apigateway.dto.request;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the rules of {@link RegisterRequest}. The validator is built without a Spring context and
 * without Jakarta EL ({@code ParameterMessageInterpolator}).
 */
class RegisterRequestTest {

  private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
      .messageInterpolator(new ParameterMessageInterpolator())
      .buildValidatorFactory().getValidator();

  private static final LocalDate BORN = LocalDate.of(1990, 5, 17);
  private static final String PASSWORD = "a-strong-password";

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
        Arguments.of("blank name",
            new RegisterRequest("  ", "Lee", BORN, "ann@example.com", PASSWORD), "name"),
        Arguments.of("no name",
            new RegisterRequest(null, "Lee", BORN, "ann@example.com", PASSWORD), "name"),
        Arguments.of("a name over 100 characters",
            new RegisterRequest("a".repeat(101), "Lee", BORN, "ann@example.com", PASSWORD),
            "name"),
        Arguments.of("no surname",
            new RegisterRequest("Ann", null, BORN, "ann@example.com", PASSWORD), "surname"),
        Arguments.of("no birth date",
            new RegisterRequest("Ann", "Lee", null, "ann@example.com", PASSWORD), "birthDate"),
        Arguments.of("a birth date in the future",
            new RegisterRequest("Ann", "Lee", LocalDate.now().plusDays(1), "ann@example.com",
                PASSWORD), "birthDate"),
        Arguments.of("an email without an at sign",
            new RegisterRequest("Ann", "Lee", BORN, "not-an-email", PASSWORD), "email"),
        Arguments.of("a blank email",
            new RegisterRequest("Ann", "Lee", BORN, "   ", PASSWORD), "email"),
        Arguments.of("an email over 255 characters",
            new RegisterRequest("Ann", "Lee", BORN,
                String.format("%s@example.com", "a".repeat(250)), PASSWORD), "email"),
        Arguments.of("a password under 8 characters",
            new RegisterRequest("Ann", "Lee", BORN, "ann@example.com", "short1"), "password"),
        Arguments.of("no password",
            new RegisterRequest("Ann", "Lee", BORN, "ann@example.com", null), "password"),
        Arguments.of("a blank password",
            new RegisterRequest("Ann", "Lee", BORN, "ann@example.com", "        "), "password"),
        Arguments.of("a password over 72 bytes",
            new RegisterRequest("Ann", "Lee", BORN, "ann@example.com", "a".repeat(73)),
            "password"));
  }

  @Test
  void aValidRequestHasNoViolations() {
    RegisterRequest request =
        new RegisterRequest("Ann", "Lee", BORN, "ann@example.com", PASSWORD);

    assertTrue(VALIDATOR.validate(request).isEmpty());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRequests")
  void anInvalidFieldIsReportedByItsName(String name, RegisterRequest request, String field) {
    Set<String> fields = VALIDATOR.validate(request).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());

    assertEquals(Set.of(field), fields);
  }

  @Test
  void toStringHoldsNoValue() {
    RegisterRequest request =
        new RegisterRequest("Ann", "Lee", BORN, "ann@example.com", PASSWORD);

    String text = request.toString();
    assertAll(
        () -> assertFalse(text.contains(PASSWORD)),
        () -> assertFalse(text.contains("ann@example.com")),
        () -> assertFalse(text.contains("Ann")));
  }
}
