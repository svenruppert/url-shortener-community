package junit.com.svenruppert.urlshortener.api.handler.urlmapping.exports;

import com.sun.net.httpserver.HttpServer;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.api.handler.urlmapping.exports.ZipWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An export that dies mid-stream must not look like a finished one.
 *
 * <p>The status line is sent before the first byte of content, so a failure
 * cannot be turned into a 500 afterwards — the previous code tried exactly that
 * and swallowed the (inevitable) failure of the second
 * {@code sendResponseHeaders}. Worse, closing the {@link java.util.zip.ZipOutputStream}
 * on the way out called {@code finish()}, which writes the central directory:
 * the truncated archive came back as a <em>valid</em> zip under HTTP 200 and
 * could be filed away as a good backup.
 *
 * <p>Now {@code finish()} runs only on the success path, so an aborted export
 * yields bytes no zip reader accepts. That is the difference these tests pin.
 */
class ZipWriterAbortTest {

  @TempDir
  static Path tempDir;

  private static HttpServer server;
  private static String baseUrl;
  private static HttpClient http;

  @BeforeAll
  static void startServer()
      throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

    server.createContext("/good", ex ->
        ZipWriter.writeZipStream(ex, HttpStatus.OK, "export.json", "export.zip",
            out -> out.write("{\"items\":[]}".getBytes(StandardCharsets.UTF_8))));

    server.createContext("/aborted", ex ->
        ZipWriter.writeZipStream(ex, HttpStatus.OK, "export.json", "export.zip",
            out -> {
              out.write("{\"items\":[".getBytes(StandardCharsets.UTF_8));
              throw new IOException("store went away half way through");
            }));

    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void aCompleteExportIsAReadableArchive()
      throws Exception {
    Path file = download("/good", "good.zip");
    try (ZipFile zip = new ZipFile(file.toFile())) {
      assertTrue(zip.getEntry("export.json") != null, "the entry must be there");
    }
  }

  @Test
  void anAbortedExportIsNotAReadableArchive()
      throws Exception {
    Path file = download("/aborted", "aborted.zip");
    // No central directory — every zip reader refuses it, which is exactly the
    // point: the consumer notices instead of storing a half backup.
    assertThrows(ZipException.class, () -> {
      try (ZipFile zip = new ZipFile(file.toFile())) {
        zip.size();
      }
    }, "an aborted export must not pass as a valid archive");
  }

  // --- helpers ----------------------------------------------------------------

  private static Path download(String path, String name)
      throws Exception {
    HttpResponse<byte[]> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray());
    // The status is 200 in both cases and cannot be anything else — it was on
    // the wire before the first byte of content.
    assertEquals(HttpStatus.OK.code(), res.statusCode());
    Path file = tempDir.resolve(name);
    Files.write(file, res.body());
    return file;
  }
}
