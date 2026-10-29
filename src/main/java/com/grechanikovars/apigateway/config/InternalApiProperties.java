package com.grechanikovars.apigateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the calls that the Gateway makes to the internal endpoints of the services, the
 * ones that only other services may call.
 *
 * <p>{@link #toString()} is overridden on purpose: the generated one would print the key.
 *
 * @param apiKey the shared key that is sent in the header {@code X-Internal-Key}; it comes from
 *     the environment variable {@code INTERNAL_API_KEY}, has no default and must be the same in
 *     every service. An empty value stops the startup
 * @param responseTimeout how long the Gateway waits for the answer of one internal call
 *     ({@code app.internal.response-timeout}, default 5 seconds)
 */
@ConfigurationProperties(prefix = "app.internal")
public record InternalApiProperties(String apiKey,
    @DefaultValue("5s") Duration responseTimeout) {

  /**
   * Checks the settings.
   *
   * @throws IllegalStateException if the key is missing or blank
   * @throws IllegalArgumentException if the timeout is not positive
   */
  public InternalApiProperties {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("app.internal.api-key (INTERNAL_API_KEY) must be set");
    }
    if (responseTimeout == null || responseTimeout.isZero() || responseTimeout.isNegative()) {
      throw new IllegalArgumentException("app.internal.response-timeout must be positive");
    }
  }

  /**
   * Describes the settings without the key.
   *
   * @return a fixed text
   */
  @Override
  public String toString() {
    return "InternalApiProperties[apiKey=***]";
  }
}
