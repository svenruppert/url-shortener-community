package com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.core.AliasPolicy;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Records which user owns which short code (P210). Kept separate from the core
 * {@code ShortUrlMapping} record so the owner model is additive. Ownership is
 * enforced by {@link OwnerGuard}; a subject with {@code link:read:all} bypasses.
 *
 * <p>The actual storage is pluggable via {@link Backend} — the same pattern as
 * {@link ApiKeyStore}. The default is in-memory (used by unit tests); the
 * persistent server installs a durable EclipseStore backend at startup
 * ({@link #useBackend(Backend)}) so ownership survives a restart (V00.17.00
 * P020 — before that, every restart silently reset all links to "unowned").
 */
public final class LinkOwnershipStore {

  public static final LinkOwnershipStore INSTANCE = new LinkOwnershipStore();

  private volatile Backend backend = new InMemoryBackend();

  private LinkOwnershipStore() {
  }

  /** Installs a storage backend (e.g. the EclipseStore-backed one at startup). */
  public void useBackend(Backend backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  /**
   * Resets to the default in-memory backend. Called when an EclipseStore-backed
   * backend is closed, so the singleton never dangles at a closed storage
   * (important for test isolation in a shared JVM).
   */
  public void resetToInMemory() {
    this.backend = new InMemoryBackend();
  }

  public void record(String shortCode, Long userId) {
    if (shortCode != null && userId != null) {
      backend.put(AliasPolicy.normalize(shortCode), userId);
    }
  }

  /** Bulk variant for backfill/import paths — one persistence round-trip. */
  public void recordAll(Map<String, Long> owners) {
    if (owners == null || owners.isEmpty()) {
      return;
    }
    Map<String, Long> normalized = new ConcurrentHashMap<>();
    owners.forEach((code, userId) -> {
      if (code != null && userId != null) {
        normalized.put(AliasPolicy.normalize(code), userId);
      }
    });
    backend.putAll(normalized);
  }

  public Optional<Long> owner(String shortCode) {
    return shortCode == null ? Optional.empty()
        : backend.owner(AliasPolicy.normalize(shortCode));
  }

  public void remove(String shortCode) {
    if (shortCode != null) {
      backend.remove(AliasPolicy.normalize(shortCode));
    }
  }

  /** True while no link has a recorded owner (drives the one-time backfill). */
  public boolean isEmpty() {
    return backend.isEmpty();
  }

  /**
   * All normalized codes owned by {@code userId} — the read-scoping set for
   * listing/export/statistics (V00.17.00 P050). Never {@code null}.
   */
  public Set<String> codesOwnedBy(Long userId) {
    return userId == null ? Set.of() : backend.codesOwnedBy(userId);
  }

  /** Pluggable storage for link ownership (in-memory default, EclipseStore in prod). */
  public interface Backend {
    void put(String normalizedCode, Long userId);

    void putAll(Map<String, Long> normalizedOwners);

    Optional<Long> owner(String normalizedCode);

    void remove(String normalizedCode);

    boolean isEmpty();

    Set<String> codesOwnedBy(Long userId);
  }

  /** Default, non-persistent backend — ownership is lost on restart (fine for tests). */
  static final class InMemoryBackend
      implements Backend {

    private final Map<String, Long> owners = new ConcurrentHashMap<>();

    @Override
    public void put(String normalizedCode, Long userId) {
      owners.put(normalizedCode, userId);
    }

    @Override
    public void putAll(Map<String, Long> normalizedOwners) {
      owners.putAll(normalizedOwners);
    }

    @Override
    public Optional<Long> owner(String normalizedCode) {
      return Optional.ofNullable(owners.get(normalizedCode));
    }

    @Override
    public void remove(String normalizedCode) {
      owners.remove(normalizedCode);
    }

    @Override
    public boolean isEmpty() {
      return owners.isEmpty();
    }

    @Override
    public Set<String> codesOwnedBy(Long userId) {
      return owners.entrySet().stream()
          .filter(e -> userId.equals(e.getValue()))
          .map(Map.Entry::getKey)
          .collect(Collectors.toUnmodifiableSet());
    }
  }
}
