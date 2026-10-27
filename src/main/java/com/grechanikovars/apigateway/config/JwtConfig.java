package com.grechanikovars.apigateway.config;

import com.grechanikovars.apigateway.security.FailClosedReactiveJwtDecoder;
import com.grechanikovars.apigateway.security.RemoteJwkSetSource;
import com.grechanikovars.apigateway.security.RoleAuthoritiesConverter;
import com.grechanikovars.apigateway.security.TokenTypeValidator;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

/**
 * Creates the decoder that verifies the access tokens and the converter that reads their role.
 *
 * <p>The Gateway is a resource server: it never signs a token. The public key comes from the JSON
 * Web Key Set of the Authentication Service (see {@link RemoteJwkSetSource}).
 *
 * <p>The beans are plain method calls too ({@code new JwtConfig().jwtDecoder(...)}), so unit
 * tests build the very same decoder without a Spring context.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtConfig {

  /**
   * Creates the decoder that checks the signature (RS256 only), the issuer, the time and the type
   * of a token.
   *
   * <p>The time comes from the application {@link Clock}, not from the system clock, and there is
   * no clock skew: the default of {@link JwtTimestampValidator} is 60 seconds, which would keep an
   * expired token alive for another minute. A token without an {@code exp} claim is rejected.
   * The other services use the same rules, so all of them agree on when a token ends.
   *
   * @param properties the address of the key set and the expected issuer
   * @param clock the application clock
   * @return the decoder; its errors are always {@code BadJwtException}
   */
  @Bean
  public ReactiveJwtDecoder jwtDecoder(SecurityProperties properties, Clock clock) {
    NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
        .withJwkSource(new RemoteJwkSetSource(properties.jwksUri(), clock))
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .build();
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
    timestampValidator.setClock(clock);
    timestampValidator.setAllowEmptyExpiryClaim(false);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
        List.<OAuth2TokenValidator<Jwt>>of(
            timestampValidator,
            new JwtIssuerValidator(properties.jwtIssuer()),
            new TokenTypeValidator())));
    return new FailClosedReactiveJwtDecoder(decoder);
  }

  /**
   * Creates the converter from a verified token to an authentication. The principal name is the
   * claim {@code sub} (the id of the account); the authority comes from the claim {@code role}.
   *
   * @return the converter
   */
  @Bean
  public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new RoleAuthoritiesConverter());
    return new ReactiveJwtAuthenticationConverterAdapter(converter);
  }
}
