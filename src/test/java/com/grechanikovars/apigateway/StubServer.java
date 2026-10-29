package com.grechanikovars.apigateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A tiny HTTP server that plays a service behind the Gateway in the tests. It records every
 * request it receives and answers with what the test has set, so a test can check what the
 * Gateway forwarded and what it passed back.
 */
public final class StubServer implements AutoCloseable {

  private static final long HANG_LIMIT_MILLIS = 3_000;

  /** How the server reacts to a request. */
  public enum Mode {
    /** Answers with the configured status, headers and body. */
    ANSWER,
    /** Does not answer (for at most three seconds), then closes the connection. */
    HANG
  }

  /**
   * A request as the server received it.
   *
   * @param method the HTTP method
   * @param target the path with the query string, as sent
   * @param headers the headers; the names are in lower case
   * @param body the body as text
   */
  public record RecordedRequest(String method, String target, Map<String, List<String>> headers,
      String body) {

    /**
     * Returns the values of a header.
     *
     * @param name the name of the header, in any case
     * @return the values joined with a comma, or {@code null} if the header was not sent
     */
    public String header(String name) {
      List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
      return values == null ? null : String.join(",", values);
    }

    /**
     * Tells whether a header was sent.
     *
     * @param name the name of the header, in any case
     * @return {@code true} if it was
     */
    public boolean hasHeader(String name) {
      return headers.containsKey(name.toLowerCase(Locale.ROOT));
    }
  }

  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
    Thread thread = new Thread(runnable, "stub-server");
    thread.setDaemon(true);
    return thread;
  });
  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private volatile int status = 200;
  private volatile String body = "{}";
  private volatile Map<String, String> responseHeaders = Map.of();
  private volatile Mode mode = Mode.ANSWER;
  private final Map<String, Answer> routes = new ConcurrentHashMap<>();

  /** The answer of one route. */
  private record Answer(int status, String body, boolean hang) {
  }

  private StubServer() throws IOException {
    this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    this.server.createContext("/", this::handle);
    this.server.setExecutor(executor);
    this.server.start();
  }

  /**
   * Starts a server on a free port. It answers {@code 200 {}} until told otherwise.
   *
   * @return the running server
   */
  public static StubServer start() {
    try {
      return new StubServer();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns the base address of the server.
   *
   * @return for example {@code http://127.0.0.1:51234}
   */
  public String uri() {
    return String.format("http://%s:%d",
        server.getAddress().getAddress().getHostAddress(), server.getAddress().getPort());
  }

  /**
   * Returns the requests received since the last {@link #reset()}.
   *
   * @return a copy of the list, in the order of arrival
   */
  public List<RecordedRequest> requests() {
    return new ArrayList<>(requests);
  }

  /**
   * Returns the last request.
   *
   * @return the request
   * @throws IllegalStateException if nothing was received
   */
  public RecordedRequest lastRequest() {
    if (requests.isEmpty()) {
      throw new IllegalStateException("The stub has received no request");
    }
    return requests.get(requests.size() - 1);
  }

  /**
   * Sets the answer.
   *
   * @param newStatus the HTTP status
   * @param newBody the body; empty means no body
   * @param newHeaders more headers of the answer
   */
  public void respondWith(int newStatus, String newBody, Map<String, String> newHeaders) {
    this.status = newStatus;
    this.body = newBody;
    this.responseHeaders = new LinkedHashMap<>(newHeaders);
  }

  /**
   * Sets the answer, without extra headers.
   *
   * @param newStatus the HTTP status
   * @param newBody the body; empty means no body
   */
  public void respondWith(int newStatus, String newBody) {
    respondWith(newStatus, newBody, Map.of());
  }

  /**
   * Sets the answer for one method and path. It wins over {@link #respondWith(int, String)}.
   *
   * @param method the HTTP method, for example {@code DELETE}
   * @param path the path without the query string
   * @param newStatus the HTTP status
   * @param newBody the body; empty means no body
   */
  public void respondTo(String method, String path, int newStatus, String newBody) {
    routes.put(routeKey(method, path), new Answer(newStatus, newBody, false));
  }

  /**
   * Makes the server stop answering for one method and path.
   *
   * @param method the HTTP method
   * @param path the path without the query string
   */
  public void hangOn(String method, String path) {
    routes.put(routeKey(method, path), new Answer(0, "", true));
  }

  /** Makes the server stop answering (see {@link Mode#HANG}). */
  public void hang() {
    this.mode = Mode.HANG;
  }

  /** Forgets the requests and goes back to {@code 200 {}}. */
  public void reset() {
    this.mode = Mode.ANSWER;
    this.status = 200;
    this.body = "{}";
    this.responseHeaders = Map.of();
    this.routes.clear();
    this.requests.clear();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      String text = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      Map<String, List<String>> headers = new LinkedHashMap<>();
      exchange.getRequestHeaders().forEach((name, values) ->
          headers.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
      String query = exchange.getRequestURI().getRawQuery();
      String target = query == null
          ? exchange.getRequestURI().getRawPath()
          : String.format("%s?%s", exchange.getRequestURI().getRawPath(), query);
      requests.add(new RecordedRequest(exchange.getRequestMethod(), target, headers, text));

      String key = routeKey(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath());
      Answer route = routes.get(key);
      if (isHanging(key)) {
        awaitEndOfHang(key);
        return;
      }
      int answerStatus = route == null ? status : route.status();
      String answerBody = route == null ? body : route.body();
      byte[] bytes = answerBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      responseHeaders.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
      if (bytes.length == 0) {
        exchange.sendResponseHeaders(answerStatus, -1);
        return;
      }
      exchange.sendResponseHeaders(answerStatus, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    }
  }

  private static String routeKey(String method, String path) {
    return String.format("%s %s", method.toUpperCase(Locale.ROOT), path);
  }

  /** Tells whether the server must not answer this route: the whole server or the route hangs. */
  private boolean isHanging(String key) {
    Answer route = routes.get(key);
    return mode == Mode.HANG || (route != null && route.hang());
  }

  /**
   * Keeps the request open for as long as the route is set to hang, at most three seconds. A
   * {@link #reset()} ends the wait at once. Afterwards the connection is closed without an
   * answer.
   */
  private void awaitEndOfHang(String key) {
    long deadline = System.nanoTime() + HANG_LIMIT_MILLIS * 1_000_000;
    try {
      while (isHanging(key) && System.nanoTime() < deadline) {
        Thread.sleep(50);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
