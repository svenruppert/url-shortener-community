package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.security.LinkOwnershipStore;
import com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.EclipseStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks proof for V00.17.00 P020: link ownership is durable — before this
 * cycle the store was an in-memory map, so every server restart silently reset
 * all links to "unowned" (and the fail-open guard then let ANY user manage
 * them). Also locks the one-time backfill (concept DECISION 1): links that
 * predate ownership persistence are assigned to the first admin on load.
 */
class LinkOwnershipPersistenceIT {

  @BeforeAll
  static void seedDirectory() {
    // Seeded admin (id 1) / user (id 2) — the backfill target must exist.
    UserDirectoryProvider.setDirectorySupplier(InMemoryUserDirectory::new);
  }

  private static EclipseStore open(Path dir) {
    return new EclipseStore(dir.toString(), new ShortCodeGenerator(1L), Clock.systemUTC());
  }

  @Test
  void ownershipSurvivesStorageRestart(@TempDir Path dir) {
    // Run 1: record an owner.
    try (EclipseStore ignored = open(dir)) {
      LinkOwnershipStore.INSTANCE.record("abc123", 7L);
      assertEquals(Optional.of(7L), LinkOwnershipStore.INSTANCE.owner("abc123"));
    }

    // Run 2: reopen the same directory — the owner must still resolve, then remove.
    try (EclipseStore ignored = open(dir)) {
      assertEquals(Optional.of(7L), LinkOwnershipStore.INSTANCE.owner("abc123"),
          "link ownership must survive a storage restart");
      LinkOwnershipStore.INSTANCE.remove("abc123");
      assertEquals(Optional.empty(), LinkOwnershipStore.INSTANCE.owner("abc123"));
    }

    // Run 3: the removal must have persisted too — and with no mappings present
    // the backfill has nothing to resurrect.
    try (EclipseStore ignored = open(dir)) {
      assertEquals(Optional.empty(), LinkOwnershipStore.INSTANCE.owner("abc123"),
          "ownership removal must survive a restart");
    }
  }

  @Test
  void backfillAssignsUnownedLinksToFirstAdmin(@TempDir Path dir) {
    String codeA;
    String codeB;

    // Run 1: create mappings WITHOUT ownership records — the pre-00.17 state.
    try (EclipseStore store = open(dir)) {
      codeA = store.getUrlMappingStore()
          .createMapping(null, "https://example.org/a", null, true).getOrThrow().shortCode();
      codeB = store.getUrlMappingStore()
          .createMapping(null, "https://example.org/b", null, true).getOrThrow().shortCode();
      assertTrue(LinkOwnershipStore.INSTANCE.owner(codeA).isEmpty(),
          "direct store writes carry no ownership — the legacy situation");
    }

    // Run 2: on load the one-time backfill assigns everything to the first admin (id 1).
    try (EclipseStore ignored = open(dir)) {
      assertEquals(Optional.of(1L), LinkOwnershipStore.INSTANCE.owner(codeA),
          "backfill must assign pre-existing links to the first admin");
      assertEquals(Optional.of(1L), LinkOwnershipStore.INSTANCE.owner(codeB));

      // Redistribution sticks and is never overwritten by a later start.
      LinkOwnershipStore.INSTANCE.record(codeB, 2L);
    }

    // Run 3: partially-filled map ⇒ backfill must NOT run again.
    try (EclipseStore ignored = open(dir)) {
      assertEquals(Optional.of(1L), LinkOwnershipStore.INSTANCE.owner(codeA));
      assertEquals(Optional.of(2L), LinkOwnershipStore.INSTANCE.owner(codeB),
          "a reassignment must never be reverted by the backfill");
    }
  }
}
