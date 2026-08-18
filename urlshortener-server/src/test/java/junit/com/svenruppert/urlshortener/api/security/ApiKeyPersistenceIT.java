package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.security.ApiKeyStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.EclipseStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks proof that API keys are durable: a key issued against a real
 * EclipseStore must still resolve after the storage is closed and reopened
 * against the same directory (i.e. a server restart). Revocation must persist
 * too. Uses a JUnit {@code @TempDir} so no repo-rooted storage is created.
 */
class ApiKeyPersistenceIT {

  private static EclipseStore open(Path dir) {
    return new EclipseStore(dir.toString(), new ShortCodeGenerator(1L), Clock.systemUTC());
  }

  @Test
  void apiKeySurvivesStorageRestart(@TempDir Path dir) {
    String key;

    // Run 1: issue a key.
    try (EclipseStore ignored = open(dir)) {
      key = ApiKeyStore.INSTANCE.issue(42L, "ci");
      assertTrue(key.startsWith("usk_"), "plaintext key returned");
      assertEquals(Optional.of(42L), ApiKeyStore.INSTANCE.resolveUser(key));
      assertEquals(1, ApiKeyStore.INSTANCE.listFor(42L).size());
    }

    // Run 2: reopen the same directory — the key must still resolve, then revoke it.
    try (EclipseStore ignored = open(dir)) {
      assertEquals(Optional.of(42L), ApiKeyStore.INSTANCE.resolveUser(key),
          "API key must survive a storage restart");
      var listed = ApiKeyStore.INSTANCE.listFor(42L);
      assertEquals(1, listed.size());
      assertEquals("ci", listed.getFirst().label());

      assertTrue(ApiKeyStore.INSTANCE.revoke(42L, listed.getFirst().id()));
      assertEquals(Optional.empty(), ApiKeyStore.INSTANCE.resolveUser(key));
    }

    // Run 3: the revocation must have persisted too.
    try (EclipseStore ignored = open(dir)) {
      assertEquals(Optional.empty(), ApiKeyStore.INSTANCE.resolveUser(key),
          "revocation must survive a restart");
      assertTrue(ApiKeyStore.INSTANCE.listFor(42L).isEmpty());
    }
  }

  /**
   * Test-isolation guard: closing an EclipseStore must reset the process-global
   * {@link ApiKeyStore} to a fresh in-memory backend, so a key issued against the
   * (now closed) store can never bleed into a later test sharing the same JVM.
   */
  @Test
  void closingStoreResetsBackendToInMemory(@TempDir Path dir) {
    String key;
    try (EclipseStore ignored = open(dir)) {
      key = ApiKeyStore.INSTANCE.issue(7L, "iso");
      assertEquals(Optional.of(7L), ApiKeyStore.INSTANCE.resolveUser(key),
          "key resolves while the store is open");
    }
    // The store is closed; the singleton must now point at a clean in-memory backend.
    assertEquals(Optional.empty(), ApiKeyStore.INSTANCE.resolveUser(key),
        "closing the store must reset ApiKeyStore to a clean in-memory backend");
    assertTrue(ApiKeyStore.INSTANCE.listFor(7L).isEmpty(),
        "no keys leak past store close");
  }
}
