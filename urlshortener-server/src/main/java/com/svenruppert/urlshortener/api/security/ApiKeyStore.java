package com.svenruppert.urlshortener.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.security.services.VersionBumper;

/**
 * API-key store facade. A key enables programmatic, non-interactive access: it
 * maps to a user, so requests carrying {@code X-API-Key} resolve to that user's
 * subject. Only the SHA-256 of the key is stored; the plaintext is shown exactly
 * once at creation.
 *
 * <p>The actual storage is pluggable via {@link Backend}. The default is
 * in-memory (used by unit tests); production installs a durable EclipseStore
 * backend at startup ({@link #useBackend(Backend)}) so keys survive a restart.
 */
public final class ApiKeyStore {

  public static final ApiKeyStore INSTANCE = new ApiKeyStore();

  static final String PREFIX = "usk_";

  private volatile Backend backend = new InMemoryBackend();

  private ApiKeyStore() {
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

  /** Issues a new key for a user and returns the plaintext (shown once). */
  public String issue(Long userId, String label) {
    // SEC-L2: snapshot the subject's security version at issue time so the
    // role-drift kill switch covers API keys like it covers tokens.
    Long versionAtIssue = UserDirectoryProvider
        .directory().findById(userId)
        .map(VersionBumper::current)
        .orElse(null);
    return backend.issue(userId, label, versionAtIssue);
  }

  /** Resolves a presented key to its owning user id, or empty if unknown. */
  public Optional<Long> resolveUser(String key) {
    return backend.resolveEntry(key).map(ApiKeyEntry::userId);
  }

  /** Resolves a presented key to its stored entry (SEC-L2: expiry/drift checks). */
  public Optional<ApiKeyEntry> resolveEntry(String key) {
    return backend.resolveEntry(key);
  }

  public List<ApiKeyEntry> listFor(Long userId) {
    return backend.listFor(userId);
  }

  /** Revokes a key by id, but only if it belongs to {@code userId}. */
  public boolean revoke(Long userId, String id) {
    return backend.revoke(userId, id);
  }

  // ---- shared helpers reused by every backend ----

  /** A fresh plaintext key ({@code usk_…}); returned to the caller exactly once. */
  public static String newPlaintextKey() {
    return PREFIX + UUID.randomUUID().toString().replace("-", "");
  }

  public static String newId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  public static Instant now() {
    return Instant.now(Clock.systemUTC());
  }

  public static String sha256(String s) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  /** Pluggable storage for API keys (in-memory default, EclipseStore in prod). */
  public interface Backend {
    String issue(Long userId, String label, Long versionAtIssue);

    Optional<ApiKeyEntry> resolveEntry(String key);

    List<ApiKeyEntry> listFor(Long userId);

    boolean revoke(Long userId, String id);
  }

  /** Default, non-persistent backend — keys are lost on restart (fine for tests). */
  static final class InMemoryBackend
      implements Backend {

    private final ConcurrentHashMap<String, ApiKeyEntry> byHash = new ConcurrentHashMap<>();

    @Override
    public String issue(Long userId, String label, Long versionAtIssue) {
      String key = newPlaintextKey();
      byHash.put(sha256(key),
          new ApiKeyEntry(newId(), userId, label == null ? "" : label, now(), versionAtIssue));
      return key;
    }

    @Override
    public Optional<ApiKeyEntry> resolveEntry(String key) {
      if (key == null || key.isBlank()) return Optional.empty();
      return Optional.ofNullable(byHash.get(sha256(key)));
    }

    @Override
    public List<ApiKeyEntry> listFor(Long userId) {
      return byHash.values().stream()
          .filter(e -> userId != null && userId.equals(e.userId()))
          .collect(Collectors.toList());
    }

    @Override
    public boolean revoke(Long userId, String id) {
      return byHash.entrySet().removeIf(en ->
          en.getValue().id().equals(id) && userId != null && userId.equals(en.getValue().userId()));
    }
  }
}
