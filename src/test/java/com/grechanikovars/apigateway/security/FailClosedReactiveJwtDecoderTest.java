package com.grechanikovars.apigateway.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grechanikovars.apigateway.LogCapture;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests {@link FailClosedReactiveJwtDecoder} with a mocked decoder.
 */
class FailClosedReactiveJwtDecoderTest {

  private static final Duration WAIT = Duration.ofSeconds(5);

  private final ReactiveJwtDecoder delegate = mock(ReactiveJwtDecoder.class);
  private final FailClosedReactiveJwtDecoder decoder = new FailClosedReactiveJwtDecoder(delegate);

  static Stream<Throwable> failuresOfTheDecoder() {
    return Stream.of(
        new IllegalStateException("Could not obtain the keys"),
        new JwtException("key source failed"),
        new RuntimeException("a bug"));
  }

  @Test
  void aVerifiedTokenPassesThrough() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("1").build();
    when(delegate.decode(anyString())).thenReturn(Mono.just(jwt));

    StepVerifier.create(decoder.decode("token")).expectNext(jwt).verifyComplete();
  }

  @Test
  void aBadJwtExceptionIsKeptAsItIs() {
    BadJwtException bad = new BadJwtException("the token is wrong");
    when(delegate.decode(anyString())).thenReturn(Mono.error(bad));

    StepVerifier.create(decoder.decode("token"))
        .expectErrorSatisfies(error -> assertSame(bad, error))
        .verify(WAIT);
  }

  @ParameterizedTest
  @MethodSource("failuresOfTheDecoder")
  void everyOtherFailureBecomesABadJwtExceptionWithAConstantMessage(Throwable failure) {
    when(delegate.decode(anyString())).thenReturn(Mono.error(failure));

    StepVerifier.create(decoder.decode("token"))
        .expectErrorSatisfies(error -> assertAll(
            () -> assertInstanceOf(BadJwtException.class, error),
            () -> assertEquals(FailClosedReactiveJwtDecoder.MESSAGE, error.getMessage()),
            () -> assertSame(failure, error.getCause())))
        .verify(WAIT);
  }

  @Test
  void anExceptionThrownByTheDelegateInsteadOfReturnedIsRejectedToo() {
    when(delegate.decode(anyString())).thenThrow(new IllegalStateException("thrown"));

    StepVerifier.create(decoder.decode("token"))
        .expectErrorSatisfies(error -> assertInstanceOf(BadJwtException.class, error))
        .verify(WAIT);
  }

  @Test
  void theLogNamesTheClassesButNeverTheMessageWithTheAddress() {
    when(delegate.decode(anyString())).thenReturn(Mono.error(new IllegalStateException(
        "Could not obtain the keys",
        new IllegalArgumentException("http://secret-host:8081/.well-known/jwks.json"))));

    try (LogCapture capture = LogCapture.attach()) {
      StepVerifier.create(decoder.decode("token")).expectError(BadJwtException.class).verify(WAIT);

      assertAll(
          () -> assertTrue(capture.contains("IllegalStateException")),
          () -> assertTrue(capture.contains("IllegalArgumentException")),
          () -> assertFalse(capture.contains("secret-host")));
    }
  }
}
