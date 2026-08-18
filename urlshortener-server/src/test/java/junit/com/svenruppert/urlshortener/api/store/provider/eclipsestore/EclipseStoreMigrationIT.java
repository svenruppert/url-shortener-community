package junit.com.svenruppert.urlshortener.api.store.provider.eclipsestore;

import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.EclipseStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real backward-compatibility migration test: opens a <b>copy of the actual old
 * production EclipseStore</b> with the <em>current</em> binary and verifies the
 * new binary would run on it in production.
 *
 * <p>The old store (e.g. {@code _backup/prod/v00.15.00/data}) predates the whole
 * campaign feature, so loading it must additively create every new DataRoot
 * partition ({@code campaigns / campaignOfLink / linkTags / linkLabels} and the
 * tag registry {@code knownTags}) while keeping existing links intact. The test
 * asserts no data loss, that the new partitions initialize, that a new tag can
 * be created on the migrated store, and that the migration + tag persist across
 * a restart.
 *
 * <p>Reads from the git-ignored repo-root {@code _backup/} tree; skips itself
 * when no old store is present (CI / clean checkouts stay green). The jSentinel
 * security sub-stores are not copied (no user data touched).
 */
class EclipseStoreMigrationIT {

  @TempDir
  Path tempDir;

  @Test
  void newBinaryLoadsAndMigratesOldProductionStore() throws Exception {
    Path oldStore = findOldMainStore();
    assumeTrue(oldStore != null, "no old EclipseStore main-store under _backup/ — skipping");

    // Read-only guarantee (1): fingerprint the original before we touch anything.
    Map<String, String> originalBefore = fingerprint(oldStore);

    Path copy = tempDir.resolve("store");
    copyStoreExcludingSecurity(oldStore, copy);

    // Read-only guarantee (2): we must only ever open the temp copy, never the original backup.
    assertTrue(copy.toAbsolutePath().startsWith(tempDir.toAbsolutePath()), "the store copy must live under @TempDir");
    assertNotEquals(oldStore.toRealPath(), copy.toRealPath(), "must not open the original backup store");

    int links;
    try (EclipseStore store = new EclipseStore(copy.toString(), new ShortCodeGenerator(1), Clock.systemUTC())) {
      links = store.getUrlMappingStore().countAll();
      assertTrue(links > 0, "existing links from the old production store must survive the migration");

      // P085: the link-meta partitions stay in DataRoot behind the plug-in
      // seam — dormant without a plug-in, but initialized and persistable.
      var linkMeta = store.getLinkMetaStorage();
      assertNotNull(linkMeta.knownTags(), "tag registry (knownTags) partition initialized on load");
      assertNotNull(linkMeta.linkTags(), "linkTags partition initialized on load");
      assertNotNull(linkMeta.campaigns(), "campaigns partition initialized on load");
      assertNotNull(linkMeta.campaignOfLink(), "campaignOfLink partition initialized on load");
      assertNotNull(linkMeta.linkLabels(), "linkLabels partition initialized on load");

      // exercise the seam against the just-migrated store (what a plug-in does)
      linkMeta.knownTags().put("migration-smoke", Boolean.TRUE);
      linkMeta.persist();
      assertTrue(linkMeta.knownTags().containsKey("migration-smoke"));
    }

    // reopen: the additive migration and the new tag must have persisted, no data lost
    try (EclipseStore reopened = new EclipseStore(copy.toString(), new ShortCodeGenerator(1), Clock.systemUTC())) {
      assertEquals(links, reopened.getUrlMappingStore().countAll(), "no link loss across the migration restart");
      assertTrue(reopened.getLinkMetaStorage().knownTags().containsKey("migration-smoke"),
          "migration + registry persisted across restart");
    }

    // Read-only guarantee (3): the original backup must be byte-for-byte untouched.
    assertEquals(originalBefore, fingerprint(oldStore),
        "the migration must NOT modify the original _backup store (size + mtime of every file unchanged)");
  }

  /** Size + last-modified fingerprint of every regular file under {@code root} (to prove it stays untouched). */
  private static Map<String, String> fingerprint(Path root) throws IOException {
    Map<String, String> sig = new TreeMap<>();
    try (Stream<Path> walk = Files.walk(root)) {
      for (Path p : (Iterable<Path>) walk::iterator) {
        if (Files.isRegularFile(p)) {
          sig.put(root.relativize(p).toString(),
              Files.size(p) + "@" + Files.getLastModifiedTime(p).toMillis());
        }
      }
    }
    return sig;
  }

  /** Newest old main app store under {@code _backup/} (has channel_0 + type dictionary, not a jSentinel sub-store). */
  private static Path findOldMainStore() throws IOException {
    Path backup = findBackupDir();
    if (backup == null) return null;
    try (Stream<Path> walk = Files.walk(backup)) {
      List<Path> candidates = walk
          .filter(Files::isDirectory)
          .filter(d -> Files.isDirectory(d.resolve("channel_0")))
          .filter(d -> Files.exists(d.resolve("PersistenceTypeDictionary.ptd")))
          .filter(d -> {
            String s = d.toString();
            return !s.contains("jsentinel-rest") && !s.contains("jsentinel-store") && !s.contains("app-store");
          })
          .toList();
      // Prefer a versioned backup (path segment like /v00.15.00/) over historical archives.
      Optional<Path> versioned = candidates.stream()
          .filter(p -> p.toString().matches(".*/v\\d.*"))
          .max(Comparator.comparing(Path::toString));
      if (versioned.isPresent()) return versioned.get();
      return candidates.stream().max(Comparator.comparing(Path::toString)).orElse(null);
    }
  }

  private static Path findBackupDir() {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (dir != null) {
      Path candidate = dir.resolve("_backup");
      if (Files.isDirectory(candidate)) return candidate;
      dir = dir.getParent();
    }
    return null;
  }

  /** Recursively copies the store, skipping the nested jSentinel security stores. */
  private static void copyStoreExcludingSecurity(Path src, Path dst) throws IOException {
    try (Stream<Path> walk = Files.walk(src)) {
      for (Path p : (Iterable<Path>) walk::iterator) {
        String rel = src.relativize(p).toString();
        if (rel.contains("jsentinel-rest")) continue;
        Path target = dst.resolve(rel);
        if (Files.isDirectory(p)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(p, target);
        }
      }
    }
  }
}
