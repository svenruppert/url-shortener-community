package junit.com.svenruppert.urlshortener.api.handler.statistics;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.svenruppert.urlshortener.api.handler.statistics.StatisticsDebugHandler;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryStatisticsStore;
import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StatisticsDebugHandler.
 */
class StatisticsDebugHandlerTest {

  private StatisticsDebugHandler handler;
  private InMemoryStatisticsStore store;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    Instant fixedInstant = LocalDateTime.of(2024, 1, 15, 12, 0, 0)
        .toInstant(ZoneOffset.UTC);
    fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    store = new InMemoryStatisticsStore(fixedClock);
    handler = new StatisticsDebugHandler(store);
  }

  @Test
  @DisplayName("should return 200 OK with debug info for GET request")
  void shouldReturn200WithDebugInfo() throws IOException {
    TestHttpExchange exchange = new TestHttpExchange("GET", "/api/v1/statistics/debug");

    handler.handle(exchange);

    assertEquals(200, exchange.getResponseCode());
    String response = exchange.getResponseBodyAsString();
    assertTrue(response.contains("statisticsEnabled"));
    assertTrue(response.contains("hotWindowDays"));
  }

  @Test
  @DisplayName("should return 405 for non-GET requests")
  void shouldReturn405ForNonGetRequests() throws IOException {
    TestHttpExchange exchange = new TestHttpExchange("POST", "/api/v1/statistics/debug");

    handler.handle(exchange);

    assertEquals(405, exchange.getResponseCode());
  }

  @Test
  @DisplayName("should include event counts in response")
  void shouldIncludeEventCountsInResponse() throws IOException {
    // Record some events
    recordEvent("abc123", 2024, 1, 15, 10, 0);
    recordEvent("abc123", 2024, 1, 15, 11, 0);
    recordEvent("xyz789", 2024, 1, 15, 10, 0);

    TestHttpExchange exchange = new TestHttpExchange("GET", "/api/v1/statistics/debug");

    handler.handle(exchange);

    assertEquals(200, exchange.getResponseCode());
    String response = exchange.getResponseBodyAsString();

    // Should contain short code counts
    assertTrue(response.contains("events_shortCodeCount"));
    assertTrue(response.contains("totalEventCount"));
  }

  @Test
  @DisplayName("should include config values in response")
  void shouldIncludeConfigValuesInResponse() throws IOException {
    TestHttpExchange exchange = new TestHttpExchange("GET", "/api/v1/statistics/debug");

    handler.handle(exchange);

    String response = exchange.getResponseBodyAsString();

    // Should contain config values
    assertTrue(response.contains("\"statisticsEnabled\":true"));
    assertTrue(response.contains("\"hotWindowDays\":7"));
    assertTrue(response.contains("\"writerBatchSize\":100"));
  }

  @Test
  @DisplayName("should include detail info when events exist")
  void shouldIncludeDetailInfoWhenEventsExist() throws IOException {
    recordEvent("testcode", 2024, 1, 15, 10, 0);

    TestHttpExchange exchange = new TestHttpExchange("GET", "/api/v1/statistics/debug");

    handler.handle(exchange);

    String response = exchange.getResponseBodyAsString();

    // Should contain detail sections
    assertTrue(response.contains("events_detail"));
    assertTrue(response.contains("testcode"));
    assertTrue(response.contains("2024-01-15"));
  }

  @Test
  @DisplayName("should return storeType in response")
  void shouldReturnStoreTypeInResponse() throws IOException {
    TestHttpExchange exchange = new TestHttpExchange("GET", "/api/v1/statistics/debug");

    handler.handle(exchange);

    String response = exchange.getResponseBodyAsString();
    assertTrue(response.contains("\"storeType\":\"InMemory\""));
  }

  private void recordEvent(String shortCode, int year, int month, int day, int hour, int minute) {
    Instant timestamp = LocalDateTime.of(year, month, day, hour, minute, 0)
        .toInstant(ZoneOffset.UTC);
    store.recordEvent(RedirectEvent.minimal(shortCode, timestamp));
  }

  /**
   * Simple HttpExchange implementation for testing.
   */
  private static class TestHttpExchange extends HttpExchange {
    private final String method;
    private final URI uri;
    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
    private int responseCode = -1;

    TestHttpExchange(String method, String path) {
      this.method = method;
      this.uri = URI.create(path);
    }

    @Override
    public Headers getRequestHeaders() {
      return requestHeaders;
    }

    @Override
    public Headers getResponseHeaders() {
      return responseHeaders;
    }

    @Override
    public URI getRequestURI() {
      return uri;
    }

    @Override
    public String getRequestMethod() {
      return method;
    }

    @Override
    public HttpContext getHttpContext() {
      return null;
    }

    @Override
    public void close() {
    }

    @Override
    public InputStream getRequestBody() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public OutputStream getResponseBody() {
      return responseBody;
    }

    @Override
    public void sendResponseHeaders(int rCode, long responseLength) {
      this.responseCode = rCode;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
      return new InetSocketAddress("127.0.0.1", 12345);
    }

    @Override
    public int getResponseCode() {
      return responseCode;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
      return new InetSocketAddress("127.0.0.1", 8080);
    }

    @Override
    public String getProtocol() {
      return "HTTP/1.1";
    }

    @Override
    public Object getAttribute(String name) {
      return null;
    }

    @Override
    public void setAttribute(String name, Object value) {
    }

    @Override
    public void setStreams(InputStream i, OutputStream o) {
    }

    @Override
    public HttpPrincipal getPrincipal() {
      return null;
    }

    public String getResponseBodyAsString() {
      return responseBody.toString();
    }
  }
}