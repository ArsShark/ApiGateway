package com.innowise.apigateway.config;

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
        () -> assertDoesNotThrow(
            () -> new ServicesProperties(VALID, "https://auth.example", VALID, VALID)),
        () -> assertEquals(VALID,
            new ServicesProperties(VALID, VALID, VALID, VALID).userUri()));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "user-service:8082", "ftp://user-service", "/api/users",
      "http://", "not a uri"})
  void aWrongUserAddressStopsTheStartup(String value) {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ServicesProperties(value, VALID, VALID, VALID));

    assertTrue(error.getMessage().contains("app.services.user-uri"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"ftp://auth", "auth:8081"})
  void aWrongAuthAddressStopsTheStartup(String value) {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ServicesProperties(VALID, value, VALID, VALID));

    assertTrue(error.getMessage().contains("app.services.auth-uri"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"ftp://order", "order:8083", "http://"})
  void aWrongOrderAddressStopsTheStartup(String value) {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ServicesProperties(VALID, VALID, value, VALID));

    assertTrue(error.getMessage().contains("app.services.order-uri"));
  }

  @Test
  void theOrderAddressIsKept() {
    assertEquals("http://order-service:8083",
        new ServicesProperties(VALID, VALID, "http://order-service:8083", VALID).orderUri());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "ftp://payment", "payment:8084", "/api/payments", "http://"})
  void aWrongPaymentAddressStopsTheStartup(String value) {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ServicesProperties(VALID, VALID, VALID, value));

    assertTrue(error.getMessage().contains("app.services.payment-uri"));
  }

  @Test
  void thePaymentAddressIsKept() {
    assertEquals("http://payment-service:8084",
        new ServicesProperties(VALID, VALID, VALID, "http://payment-service:8084").paymentUri());
  }
}
