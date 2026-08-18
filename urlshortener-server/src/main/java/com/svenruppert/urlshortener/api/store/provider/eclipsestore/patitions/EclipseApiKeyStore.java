package com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.ApiKeyEntry;
import com.svenruppert.urlshortener.api.security.ApiKeyStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.DataRoot;
import org.eclipse.store.storage.types.StorageManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * EclipseStore-backed API-key backend: entries live in {@link DataRoot#apiKeys()}
 * (hash → {@link ApiKeyEntry}) and are persisted on every mutation, so keys
 * survive a server restart. Only the SHA-256 hash is stored, never the plaintext.
 */
public final class EclipseApiKeyStore
    implements ApiKeyStore.Backend, HasLogger {

  private final StorageManager storage;

  public EclipseApiKeyStore(StorageManager storage) {
    this.storage = storage;
  }

  private Map<String, ApiKeyEntry> keys() {
    return ((DataRoot) storage.root()).apiKeys();
  }

  @Override
  public String issue(Long userId, String label, Long versionAtIssue) {
    String key = ApiKeyStore.newPlaintextKey();
    keys().put(ApiKeyStore.sha256(key),
        new ApiKeyEntry(ApiKeyStore.newId(), userId, label == null ? "" : label,
            ApiKeyStore.now(), versionAtIssue));
    storage.store(keys());
    logger().info("api key persisted for user id={}", userId);
    return key;
  }

  @Override
  public Optional<ApiKeyEntry> resolveEntry(String key) {
    if (key == null || key.isBlank()) return Optional.empty();
    return Optional.ofNullable(keys().get(ApiKeyStore.sha256(key)));
  }

  @Override
  public List<ApiKeyEntry> listFor(Long userId) {
    return keys().values().stream()
        .filter(e -> userId != null && userId.equals(e.userId()))
        .collect(Collectors.toList());
  }

  @Override
  public boolean revoke(Long userId, String id) {
    boolean removed = keys().entrySet().removeIf(en ->
        en.getValue().id().equals(id) && userId != null && userId.equals(en.getValue().userId()));
    if (removed) storage.store(keys());
    return removed;
  }
}
