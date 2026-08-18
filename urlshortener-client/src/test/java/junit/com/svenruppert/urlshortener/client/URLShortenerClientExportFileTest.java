package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class URLShortenerClientExportFileTest
    implements HasLogger {

  private ShortenerServer server;
  private URLShortenerClient client;

  @BeforeEach
  void setUp()
      throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init();
    TestAuth.loginAsAdmin();
    client = new URLShortenerClient();
  }

  @AfterEach
  void tearDown() {
    server.shutdown();
  }

  @Test
  void exportAllAsZipFile_shouldWriteZipFileWithJson(@TempDir Path tempDir)
      throws IOException {

    // Arrange
    final String url = "https://svenruppert.com";
    client.createMapping(url);

    // Act
    Path zipFile = client.exportAllAsZipFile(null, tempDir);

    // Assert: Datei existiert
    assertNotNull(zipFile);
    assertTrue(Files.exists(zipFile), "ZIP file should exist");
    assertTrue(zipFile.getFileName().toString().toLowerCase().endsWith(".zip"),
               "ZIP filename should end with .zip");

    // Assert: ZIP-Inhalt prüfen
    boolean foundJson = false;
    String jsonPayload = null;

    try (InputStream fis = Files.newInputStream(zipFile);
         ZipInputStream zis = new ZipInputStream(fis)) {

      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".json")) {
          foundJson = true;
          jsonPayload = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
          logger().info("Found Zip Entry {}", jsonPayload);
          break;
        }
      }
    }

    assertTrue(foundJson, "ZIP should contain at least one JSON entry");
    assertNotNull(jsonPayload, "JSON payload should not be null");
    assertTrue(jsonPayload.contains("\"items\""),
               "JSON should contain items section");
    assertTrue(jsonPayload.contains(url),
               "JSON should contain the created originalUrl");
  }
}
