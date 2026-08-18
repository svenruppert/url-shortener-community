package junit.com.svenruppert.urlshortener.client;

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

import static org.junit.jupiter.api.Assertions.*;

public class URLShortenerClientExportDownloadTest {

  private ShortenerServer server;
  private URLShortenerClient client;

  @BeforeEach
  public void setUp() throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init();
    TestAuth.loginAsAdmin();
    client = new URLShortenerClient();
  }

  @AfterEach
  public void tearDown() {
    server.shutdown();
  }

  @Test
  void exportAllAsZipDownload_shouldProvideFilenameViaHEAD_andFactoryReconnects()
      throws IOException {

    // Arrange
    final String url = "https://svenruppert.com";
    client.createMapping(url);

    // Act
    final URLShortenerClient.ExportZipDownload download =
        client.exportAllAsZipDownload((UrlMappingListRequest) null);

    // Assert: filename
    assertNotNull(download);
    assertNotNull(download.filename());
    assertTrue(download.filename().toLowerCase().endsWith(".zip"), "filename should end with .zip");
    assertTrue(download.filename().startsWith("urlshortener-export"),
               "filename should start with urlshortener-export");

    // Assert: factory can be used multiple times; each time yields a valid zip containing JSON with our mapping
    assertZipContainsUrl(download.inputStreamFactory().get(), url);
    assertZipContainsUrl(download.inputStreamFactory().get(), url);
  }

  private static void assertZipContainsUrl(InputStream in, String url) throws IOException {
    assertNotNull(in);

    boolean foundJson = false;
    String jsonPayload = null;

    try (InputStream closeMe = in;
         ZipInputStream zis = new ZipInputStream(closeMe)) {

      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".json")) {
          foundJson = true;
          jsonPayload = readAll(zis);
          break;
        }
      }
    }

    assertTrue(foundJson, "ZIP export should contain at least one .json entry");
    assertNotNull(jsonPayload, "JSON payload should not be null");
    assertTrue(jsonPayload.contains(url), "JSON should contain the created originalUrl");
  }

  private static String readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8 * 1024];
    int n;
    while ((n = in.read(buffer)) >= 0) {
      out.write(buffer, 0, n);
    }
    return out.toString(StandardCharsets.UTF_8);
  }
}
