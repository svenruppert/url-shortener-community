package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class URLShortenerClientExportTest
    implements HasLogger {

  private ShortenerServer server;
  private URLShortenerClient client;

  private static String readAll(InputStream in)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8 * 1024];
    int n;
    while ((n = in.read(buffer)) >= 0) {
      out.write(buffer, 0, n);
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  @BeforeEach
  public void startServer()
      throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init();
    TestAuth.loginAsAdmin();
    client = new URLShortenerClient();
  }

  @AfterEach
  public void stopServer() {
    server.shutdown();
  }

  @Test
  void exportAllAsZipStream_shouldContainJsonWithCreatedMapping()
      throws IOException {

    // Arrange
    final String url = "https://svenruppert.com";
    client.createMapping(url);

    // request=null => export ohne Filter (alles)
    final UrlMappingListRequest request = null;

    // Act
    try (InputStream in = client.exportAllAsZipStream(request)) {
      assertNotNull(in);

      // Assert: ZIP muss mindestens einen Entry enthalten, der JSON enthält
      boolean foundJson = false;
      String jsonPayload = null;

      try (ZipInputStream zis = new ZipInputStream(in)) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".json")) {
            foundJson = true;
            jsonPayload = readAll(zis);
            logger().info("Found Zip Entry {}", jsonPayload);
            break;
          }
        }
      }

      assertTrue(foundJson, "ZIP export should contain at least one .json entry");
      assertNotNull(jsonPayload, "JSON payload should not be null");

      // Minimalchecks auf Struktur und Daten
      assertTrue(jsonPayload.contains("\"items\""), "JSON should contain items array/object");
      assertTrue(jsonPayload.contains(url), "JSON should contain the created originalUrl");
    }
  }
}
