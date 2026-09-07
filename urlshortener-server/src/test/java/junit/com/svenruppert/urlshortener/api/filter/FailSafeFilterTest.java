package junit.com.svenruppert.urlshortener.api.filter;

import com.sun.net.httpserver.HttpServer;
import com.svenruppert.urlshortener.api.filter.FailSafeFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static com.svenruppert.dependencies.core.net.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The net that guarantees an answer, exercised against a real {@link HttpServer}
 * with real handlers — one that throws, one that answers normally.
 *
 * <p>Without the filter the JDK server closes the connection when a handler
 * throws, and the client sees {@code "header parser received no bytes"} rather
 * than a status it can act on.
 */
class FailSafeFilterTest {

  private static HttpServer server;
  private static String baseUrl;
  private static HttpClient http;

  @BeforeAll
  static void startServer()
      throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

    var boom = server.createContext("/boom", exchange -> {
      throw new IllegalStateException("handler blew up");
    });
    boom.getFilters().add(new FailSafeFilter());

    var fine = server.createContext("/fine", exchange -> {
      byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(OK.code(), body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    fine.getFilters().add(new FailSafeFilter());

    // Throws AFTER the response is already on the wire — the filter must not
    // try to write a second status line onto the same exchange.
    var late = server.createContext("/late", exchange -> {
      byte[] body = "{\"partial\":true}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(OK.code(), body.length);
      exchange.getResponseBody().write(body);
      throw new IllegalStateException("blew up after responding");
    });
    late.getFilters().add(new FailSafeFilter());

    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void aThrowingHandlerStillProducesAResponse()
      throws Exception {
    var res = get("/boom");
    assertEquals(INTERNAL_SERVER_ERROR.code(), res.statusCode(),
        "an escaped exception must become a 500, not a dropped connection");
    assertTrue(res.body().contains("internal_error"), "with a body the client can read: " + res.body());
  }

  @Test
  void headOnAThrowingHandlerGetsNoBody()
      throws Exception {
    var res = send(HttpRequest.newBuilder(URI.create(baseUrl + "/boom"))
        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build());
    assertEquals(INTERNAL_SERVER_ERROR.code(), res.statusCode());
    assertTrue(res.body() == null || res.body().isEmpty(),
        "HEAD never carries a body, got: " + res.body());
  }

  @Test
  void aNormalResponsePassesThroughUntouched()
      throws Exception {
    var res = get("/fine");
    assertEquals(OK.code(), res.statusCode(), "the filter must not disturb the happy path");
    assertEquals("{\"ok\":true}", res.body());
  }

  @Test
  void aFailureAfterTheResponseLeavesTheResponseAlone()
      throws Exception {
    var res = get("/late");
    assertEquals(OK.code(), res.statusCode(),
        "the status was already sent — the filter must not attempt to overwrite it");
    assertEquals("{\"partial\":true}", res.body());
  }

  // --- helpers ----------------------------------------------------------------

  private static HttpResponse<String> get(String path)
      throws Exception {
    return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
  }

  private static HttpResponse<String> send(HttpRequest request)
      throws Exception {
    var res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertNotNull(res, "a response is exactly what this filter guarantees");
    return res;
  }
}
