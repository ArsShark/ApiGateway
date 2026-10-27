package com.grechanikovars.apigateway;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application clock in the tests that start the whole application, with a clock
 * that stands five minutes after the issue time of {@link TestTokens}, so the test tokens are
 * valid and a test can move the time.
 *
 * <p>It is a {@code @TestConfiguration}, never a plain {@code @Configuration}: the component
 * scan of the application would find a plain one in every test context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

  /** The value of the clock at the start of every test. */
  public static final Instant START = TestTokens.ISSUED_AT.plus(Duration.ofMinutes(5));

  /**
   * Creates the movable clock; it wins over the clock of the application.
   *
   * @return the clock
   */
  @Bean
  @Primary
  public MutableClock testClock() {
    return new MutableClock(START);
  }
}
