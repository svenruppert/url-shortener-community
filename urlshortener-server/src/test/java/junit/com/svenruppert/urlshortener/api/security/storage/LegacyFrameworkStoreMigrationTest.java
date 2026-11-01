package junit.com.svenruppert.urlshortener.api.security.storage;

import com.svenruppert.urlshortener.api.security.storage.LegacyFrameworkStoreMigration;
import com.svenruppert.urlshortener.api.security.storage.LegacyFrameworkStoreMigration.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFrameworkStoreMigrationTest {

  @Test
  void retiresAnOrphanedLegacyStoreAndPreservesItsContents(@TempDir Path baseDir)
      throws IOException {
    Path legacyChannel = Files.createDirectories(
        baseDir.resolve("jsentinel-store").resolve("channel_0"));
    Files.writeString(legacyChannel.resolve("channel_0_0.dat"), "audit-bytes");

    LegacyFrameworkStoreMigration.Result result =
        LegacyFrameworkStoreMigration.retireOrphanedLegacyStore(baseDir);

    assertEquals(Outcome.RETIRED, result.outcome());
    assertNotNull(result.retiredTo());
    assertFalse(Files.exists(baseDir.resolve("jsentinel-store")),
        "the plain legacy directory must be gone after the rename");
    assertTrue(Files.isDirectory(result.retiredTo()), "the retired directory must exist");
    assertTrue(result.retiredTo().getFileName().toString().startsWith("jsentinel-store.retired-"),
        "the retired directory keeps a recognizable, timestamped name");
    assertEquals("audit-bytes",
        Files.readString(result.retiredTo().resolve("channel_0").resolve("channel_0_0.dat")),
        "the rename must preserve the store's contents byte-for-byte");
  }

  @Test
  void doesNothingWhenNoLegacyStoreIsPresent(@TempDir Path baseDir)
      throws IOException {
    // A current jcustos-store on its own is the normal case and must not be touched.
    Files.createDirectories(baseDir.resolve("jcustos-store"));

    LegacyFrameworkStoreMigration.Result result =
        LegacyFrameworkStoreMigration.retireOrphanedLegacyStore(baseDir);

    assertEquals(Outcome.NO_LEGACY_STORE, result.outcome());
    assertNull(result.retiredTo());
    assertTrue(Files.isDirectory(baseDir.resolve("jcustos-store")));
    try (Stream<Path> entries = Files.list(baseDir)) {
      assertEquals(1, entries.count(), "nothing new must be created");
    }
  }

  @Test
  void isIdempotentAcrossRepeatedStarts(@TempDir Path baseDir)
      throws IOException {
    Files.createDirectories(baseDir.resolve("jsentinel-store"));

    assertEquals(Outcome.RETIRED,
        LegacyFrameworkStoreMigration.retireOrphanedLegacyStore(baseDir).outcome());
    // A second start no longer sees a plain legacy directory.
    assertEquals(Outcome.NO_LEGACY_STORE,
        LegacyFrameworkStoreMigration.retireOrphanedLegacyStore(baseDir).outcome());
  }
}
