package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.StatisticsClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.statistics.StatisticsImportResponse;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import com.svenruppert.urlshortener.core.urlmapping.imports.ImportResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * No-mocks backward-compatibility test driven by <b>real exports from the old
 * system</b>. Fixtures are read from the repo-root {@code _backup/} tree
 * (git-ignored, e.g. {@code _backup/prod/v00.15.00/urlshortener-export-*.zip});
 * the newest matching export per type is used. When no backup is present each
 * test skips itself (JUnit assumption), so CI and clean checkouts stay green.
 *
 * <p>It feeds the legacy export through the <em>current</em> importers against a
 * fresh in-process server and asserts the old format still imports cleanly.
 */
class LegacyImportFixtureIT {

  private ShortenerServer server;
  private String adminUrl;
  private URLShortenerClient client;

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
  void legacyLinksExport_importsCleanlyOnFreshStore() throws Exception {
    Path zipPath = latestBackup(n -> n.startsWith("urlshortener-export-") && n.endsWith(".zip"));
    assumeTrue(zipPath != null, "no links export under _backup/ — skipping");
    byte[] zip = Files.readAllBytes(zipPath);

    Map<?, ?> preview = JacksonJson.mapper().readValue(client.importValidateRaw(zip), Map.class);
    int newItems = ((Number) preview.get("newItems")).intValue();
    int conflicts = ((Number) preview.get("conflicts")).intValue();
    int invalid = ((Number) preview.get("invalid")).intValue();
    String stagingId = String.valueOf(preview.get("stagingId"));

    assertTrue(newItems > 0, "the real export must contain links: " + preview);
    assertEquals(0, invalid, "the current importer must accept every legacy entry: " + preview.get("invalidItems"));
    assertEquals(0, conflicts, "fresh store → no conflicts");

    ImportResult r = client.importApply(stagingId);
    assertEquals(newItems, r.created(), "all legacy links created 1:1");
    assertEquals(newItems, client.listCount(UrlMappingListRequest.builder().build()), "store holds the imported links");
  }

  @Test
  void legacyStatisticsExport_importsCleanly() throws Exception {
    Path zipPath = latestBackup(n -> n.startsWith("urlshortener-statistics-export-") && n.endsWith(".zip"));
    assumeTrue(zipPath != null, "no statistics export under _backup/ — skipping");

    StatisticsImportResponse resp = new StatisticsClient(adminUrl).importZip(Files.readAllBytes(zipPath));
    assertTrue(resp.importedEvents() > 0, "the real statistics export must import events: " + resp);
    assertEquals(0, resp.skippedLines(), "the current importer must accept every legacy line: " + resp);
  }

  /** Newest file anywhere under the repo-root {@code _backup/} tree matching the predicate, or null. */
  private static Path latestBackup(Predicate<String> nameMatches) throws IOException {
    Path backup = findBackupDir();
    if (backup == null) return null;
    try (Stream<Path> walk = Files.walk(backup)) {
      Optional<Path> newest = walk
          .filter(Files::isRegularFile)
          .filter(p -> nameMatches.test(p.getFileName().toString()))
          .max(Comparator.comparing(p -> p.getFileName().toString())); // timestamped names sort chronologically
      return newest.orElse(null);
    }
  }

  /** Walks up from the working directory to find the repo-root {@code _backup} folder. */
  private static Path findBackupDir() {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (dir != null) {
      Path candidate = dir.resolve("_backup");
      if (Files.isDirectory(candidate)) return candidate;
      dir = dir.getParent();
    }
    return null;
  }
}
