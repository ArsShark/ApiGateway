package com.grechanikovars.apigateway.config;

import java.net.URI;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The internal addresses of the services that the Gateway routes to.
 *
 * <p>Both values are checked when the settings are bound, so a wrong value stops the startup
 * instead of failing on the first request.
 *
 * @param userUri the base address of the User Service ({@code USER_SERVICE_URI}), for example
 *     {@code http://user-service:8082}
 * @param authUri the base address of the Authentication Service ({@code AUTH_SERVICE_URI}). The
 *     defaults depend on the Spring profile and are defined in the profile files
 */
@ConfigurationProperties(prefix = "app.services")
public record ServicesProperties(String userUri, String authUri) {

  /**
   * Checks the settings.
   *
   * @throws IllegalArgumentException if an address is not an absolute {@code http} or
   *     {@code https} URI with a host
   */
  public ServicesProperties {
    requireHttpUri("app.services.user-uri", userUri);
    requireHttpUri("app.services.auth-uri", authUri);
  }

  private static void requireHttpUri(String name, String value) {
    String message = String.format("%s must be an absolute http or https URI with a host", name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(message, e);
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    boolean httpScheme = scheme.equals("http") || scheme.equals("https");
    if (!httpScheme || uri.getHost() == null) {
      throw new IllegalArgumentException(message);
    }
  }
}
