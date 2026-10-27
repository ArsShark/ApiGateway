package com.grechanikovars.apigateway.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the checks of {@link ServicesProperties}.
 */
class ServicesPropertiesTest {

  private static final String VALID = "http://user-service:8082";

  @Test
  void validAddressesAreAccepted() {
    assertAll(
        () -> assertDoesNotThrow(() -> new ServicesProperties(VALID, "https://auth.example")),
        () -> assertEquals(VALID, new ServicesProperties(VALID, VALID).userUri()));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "user-service:8082", "ftp://user-service", "/api/users",
      "http://", "not a uri"})
  void aWrongUserAddressStopsTheStartup(String value) {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ServicesProperties(value, VALID));

    assertTrue(error.getMessage().contains("app.services.user-uri"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"ftp://auth", "auth:8081"})
  void aWrongAuthAddressStopsTheStartup(String value) {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ServicesProperties(VALID, value));

    assertTrue(error.getMessage().contains("app.services.auth-uri"));
  }
}
