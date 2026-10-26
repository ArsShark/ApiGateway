package com.grechanikovars.apigateway.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ClockConfig}.
 */
class ClockConfigTest {

  @Test
  void theClockRunsInUtc() {
    Clock clock = new ClockConfig().clock();

    assertAll(
        () -> assertEquals(ZoneOffset.UTC, clock.getZone()),
        () -> assertNotNull(clock.instant()));
  }
}
