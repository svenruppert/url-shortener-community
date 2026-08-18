package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.AuthContext;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.urlmapping.imports.ImportResult;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.EXPORT_FILE_NAME;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_IMPORT_CONFLICTS;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_IMPORT_INVALID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks IT for the links ZIP import flow against the real in-process server:
 * a crafted export ZIP with a conflicting shortcode, a fresh valid link and two
 * invalid entries (bad URL + missing shortcode) is validated (preview counts +
 * conflict/invalid list endpoints), then applied — only the fresh link is
 * created, the conflict is skipped and the existing link is left untouched.
 */
class LinksImportIT {

  private ShortenerServer server;
  private String adminUrl;
  private URLShortenerClient client;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void start() throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    String redirectUrl = "http://" + DEFAULT_SERVER_HOST + ":" + server.getPortRedirect();
    TestAuth.loginAsAdmin(adminUrl);
    client = new URLShortenerClient(adminUrl, redirectUrl);
  }

  @AfterEach
  void stop() {
    server.shutdown();
    TestAuth.clear();
  }

  @Test
  void validateAndApply_conflictsAndInvalidEntries() throws Exception {
    // an existing link that the import will conflict with (same shortcode, different URL)
    client.createCustomMapping("dup", "https://original.example.com");

    byte[] zip = craftExportZip("""
        {"formatVersion":"1","mode":"filtered","exportedAt":"2026-01-01T00:00:00Z","total":4,"items":[
          {"shortCode":"dup","originalUrl":"https://conflict.example.com","createdAt":"2026-01-01T00:00:00Z","expiresAt":"","active":"true"},
          {"shortCode":"fresh","originalUrl":"https://fresh.example.com","createdAt":"2026-01-01T00:00:00Z","expiresAt":"","active":"true"},
          {"shortCode":"bad","originalUrl":"not a url","createdAt":"2026-01-01T00:00:00Z","expiresAt":"","active":"true"},
          {"originalUrl":"https://missing.example.com","createdAt":"2026-01-01T00:00:00Z","expiresAt":"","active":"true"}
        ]}""");

    // ── validate: preview counts ────────────────────────────────────────────────
    Map<?, ?> preview = JacksonJson.mapper().readValue(client.importValidateRaw(zip), Map.class);
    assertEquals(1, ((Number) preview.get("newItems")).intValue(), "one fresh, valid link");
    assertEquals(1, ((Number) preview.get("conflicts")).intValue(), "dup conflicts");
    assertEquals(2, ((Number) preview.get("invalid")).intValue(), "bad url + missing shortcode");
    String stagingId = String.valueOf(preview.get("stagingId"));
    assertTrue(stagingId != null && !stagingId.isBlank());

    // ── conflict + invalid list endpoints ───────────────────────────────────────
    assertTrue(get(PATH_ADMIN_IMPORT_CONFLICTS + "?stagingId=" + stagingId + "&page=1&size=50").contains("dup"),
        "conflicts endpoint lists the conflicting shortcode");
    String invalid = get(PATH_ADMIN_IMPORT_INVALID + "?stagingId=" + stagingId + "&page=1&size=50");
    assertTrue(invalid.contains("bad"), "invalid endpoint lists the bad-url entry");

    // ── apply: only the fresh link is created, conflict skipped ──────────────────
    ImportResult r = client.importApply(stagingId);
    assertEquals(1, r.created());
    assertEquals(1, r.skippedConflicts());
    assertEquals(2, r.invalid());

    assertEquals("https://original.example.com",
        client.getMapping("dup").orElseThrow().originalUrl(), "conflict must not overwrite the existing link");
    assertEquals("https://fresh.example.com",
        client.getMapping("fresh").orElseThrow().originalUrl(), "fresh link created");
  }

  private static byte[] craftExportZip(String json) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry(EXPORT_FILE_NAME + ".json"));
      zip.write(json.getBytes(UTF_8));
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  private String get(String relativePath) throws Exception {
    HttpRequest req = HttpRequest.newBuilder(URI.create(adminUrl + relativePath))
        .header("Authorization", "Bearer " + AuthContext.getGlobalToken())
        .GET().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8)).body();
  }
}
