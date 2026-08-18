package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static org.junit.jupiter.api.Assertions.*;

public class URLShortenerClient_CreateCustomMappingTest {

  private ShortenerServer server;
  private URLShortenerClient client;
  private String baseUrlAdmin;
  private String baseUrlRedirect;

  private static void waitUntilOpen(String host, int port, Duration timeout)
      throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    Throwable last = null;
    while (System.nanoTime() < deadline) {
      try (Socket s = new Socket(host, port)) {
        return; // verbunden
      } catch (Throwable t) {
        last = t;
        Thread.sleep(25);
      }
    }
    throw new IllegalStateException("Server did not open " + host + ":" + port + " in time", last);
  }

  @BeforeEach
  void startServer()
      throws Exception {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0); // 0 = auto-assign
    waitUntilOpen(DEFAULT_SERVER_HOST, server.getPortRedirect(), Duration.ofSeconds(2));

    baseUrlAdmin = "http://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin() + "/";
    baseUrlRedirect = "http://" + DEFAULT_SERVER_HOST + ":" + server.getPortRedirect() + "/";
    client = new URLShortenerClient(baseUrlAdmin,baseUrlRedirect);
    TestAuth.loginAsAdmin(baseUrlAdmin);
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.shutdown();
    }
  }

  @Test
  void createCustomMapping_realServer_success()
      throws IOException {
    var mapping = client.createCustomMapping("java", "https://example.org/page");

    assertNotNull(mapping);
    assertEquals("java", mapping.shortCode());
    assertEquals("https://example.org/page", mapping.originalUrl());
    assertTrue(mapping.createdAt().isBefore(Instant.now().plusSeconds(2)));
  }

  @Test
  void createCustomMapping_realServer_conflict()
      throws IOException {
    // Erstes Mapping erzeugen
    client.createCustomMapping("dupe", "https://example.org/a");

    // Zweiter Versuch mit gleichem Alias => 409 → IllegalArgumentException
    var ex = assertThrows(IllegalArgumentException.class, () ->
        client.createCustomMapping("dupe", "https://example.org/b"));

    // Text ist implementation-abhängig; nur grob prüfen:
    assertTrue(ex.getMessage() != null && !ex.getMessage().isBlank());
  }

  @Test
  void createCustomMapping_realServer_invalidUrl() {
    var ex = assertThrows(IllegalArgumentException.class, () ->
        client.createCustomMapping("test", "ht!tp:/bad-url"));

    // nur grobe Prüfung, da Servertext variieren kann:
    assertTrue(ex.getMessage() == null || ex.getMessage().toLowerCase().contains("bad")
                   || ex.getMessage().toLowerCase().contains("invalid"));
  }

  @Test
  void createCustomMapping_aliasTooShort_returns400WithReason() {
    var ex = assertThrows(IllegalArgumentException.class, () ->
        client.createCustomMapping("ab", "https://example.org")); // len 2

    assertTrue(ex.getMessage().toLowerCase().contains("too short"));
  }

  @Test
  void createCustomMapping_invalidChars_returns400WithReason() {
    var ex = assertThrows(IllegalArgumentException.class, () ->
        client.createCustomMapping("bad*", "https://example.org"));

    assertTrue(ex.getMessage().toLowerCase().contains("invalid characters"));
  }

  @Test
  void createCustomMapping_reserved_returns400WithReason() {
    var ex = assertThrows(IllegalArgumentException.class, () ->
        client.createCustomMapping("api", "https://example.org"));

    assertTrue(ex.getMessage().toLowerCase().contains("reserved"));
  }
}
