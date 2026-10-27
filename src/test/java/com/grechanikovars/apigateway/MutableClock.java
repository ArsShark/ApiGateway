package com.grechanikovars.apigateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock that a test moves by hand, so expiry and the minimum time between two fetches of the
 * key set can be tested without waiting. It runs in UTC.
 */
public final class MutableClock extends Clock {

  private final AtomicReference<Instant> now;

  /**
   * Creates the clock.
   *
   * @param start the first value of the clock
   */
  public MutableClock(Instant start) {
    this.now = new AtomicReference<>(start);
  }

  /**
   * Sets the clock to a moment.
   *
   * @param instant the new value
   */
  public void set(Instant instant) {
    now.set(instant);
  }

  /**
   * Moves the clock forward.
   *
   * @param duration how far
   */
  public void advance(Duration duration) {
    now.updateAndGet(current -> current.plus(duration));
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("The test clock runs in UTC only");
  }

  @Override
  public Instant instant() {
    return now.get();
  }
}
