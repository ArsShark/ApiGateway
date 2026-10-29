package com.grechanikovars.apigateway.config;

import com.grechanikovars.apigateway.security.InternalKeyRemovalFilter;
import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Creates the HTTP clients that the Gateway uses for its own calls to the internal endpoints
 * (the registration flow). They are not the routes: a route forwards the request of a client, a
 * client of this class makes a call of the Gateway itself.
 *
 * <p>Every client sends the internal key in the header {@code X-Internal-Key} and has a timeout
 * for the connection (2 seconds) and for the answer ({@code app.internal.response-timeout}). The
 * key is set once here, so no code that makes a call can forget it or log it.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalClientsConfig {

  /**
   * The bean name of the {@code WebClient} of the User Service. It must differ from the default
   * bean name of the class {@code UserServiceClient} that uses it ({@code userServiceClient}):
   * two beans with one name stop the application at startup.
   */
  public static final String USER_SERVICE_WEB_CLIENT = "userServiceWebClient";

  /**
   * The bean name of the {@code WebClient} of the Authentication Service. It must differ from the
   * default bean name of the class {@code AuthServiceClient} that uses it
   * ({@code authServiceClient}).
   */
  public static final String AUTH_SERVICE_WEB_CLIENT = "authServiceWebClient";

  private static final int CONNECT_TIMEOUT_MILLIS = 2_000;

  /**
   * Creates the client of the User Service.
   *
   * @param services the addresses of the services
   * @param internal the key and the timeout of the internal calls
   * @return the client
   */
  @Bean(USER_SERVICE_WEB_CLIENT)
  public WebClient userServiceWebClient(ServicesProperties services,
      InternalApiProperties internal) {
    return create(services.userUri(), internal);
  }

  /**
   * Creates the client of the Authentication Service.
   *
   * @param services the addresses of the services
   * @param internal the key and the timeout of the internal calls
   * @return the client
   */
  @Bean(AUTH_SERVICE_WEB_CLIENT)
  public WebClient authServiceWebClient(ServicesProperties services,
      InternalApiProperties internal) {
    return create(services.authUri(), internal);
  }

  private static WebClient create(String baseUri, InternalApiProperties internal) {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
        .responseTimeout(internal.responseTimeout());
    return WebClient.builder()
        .baseUrl(baseUri)
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .defaultHeader(InternalKeyRemovalFilter.HEADER, internal.apiKey())
        .build();
  }
}
