package com.grechanikovars.apigateway.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the checks of {@link InternalApiProperties}.
 */
class InternalApiPropertiesTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @Test
  void aKeyAndATimeoutAreAccepted() {
    InternalApiProperties properties = new InternalApiProperties("a-key", TIMEOUT);

    assertAll(
        () -> assertEquals("a-key", properties.apiKey()),
        () -> assertEquals(TIMEOUT, properties.responseTimeout()));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void aMissingKeyStopsTheStartupWithAMessageThatNamesTheVariable(String key) {
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new InternalApiProperties(key, TIMEOUT));

    assertTrue(error.getMessage().contains("INTERNAL_API_KEY"));
  }

  @Test
  void theTimeoutMustBePositive() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class,
            () -> new InternalApiProperties("a-key", Duration.ZERO)),
        () -> assertThrows(IllegalArgumentException.class,
            () -> new InternalApiProperties("a-key", Duration.ofSeconds(-1))),
        () -> assertThrows(IllegalArgumentException.class,
            () -> new InternalApiProperties("a-key", null)));
  }

  @Test
  void toStringHidesTheKey() {
    assertFalse(new InternalApiProperties("the-secret-key", TIMEOUT).toString()
        .contains("the-secret-key"));
  }
}
