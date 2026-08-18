package com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.LinkOwnershipStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.DataRoot;
import org.eclipse.store.storage.types.StorageManager;

import java.util.Map;
import java.util.Optional;

/**
 * EclipseStore-backed link-ownership backend: entries live in
 * {@link DataRoot#linkOwners()} (normalized shortCode → user id) and are
 * persisted on every mutation, so the owner boundary survives a server restart
 * (V00.17.00 P020 — the in-memory default silently reset every link to
 * "unowned" on restart, reopening the cross-user IDOR window).
 */
public final class EclipseLinkOwnershipStore
    implements LinkOwnershipStore.Backend, HasLogger {

  private final StorageManager storage;

  public EclipseLinkOwnershipStore(StorageManager storage) {
    this.storage = storage;
  }

  private Map<String, Long> owners() {
    return ((DataRoot) storage.root()).linkOwners();
  }

  @Override
  public void put(String normalizedCode, Long userId) {
    owners().put(normalizedCode, userId);
    storage.store(owners());
  }

  @Override
  public void putAll(Map<String, Long> normalizedOwners) {
    owners().putAll(normalizedOwners);
    storage.store(owners());
    logger().info("link ownership persisted for {} codes", normalizedOwners.size());
  }

  @Override
  public Optional<Long> owner(String normalizedCode) {
    return Optional.ofNullable(owners().get(normalizedCode));
  }

  @Override
  public void remove(String normalizedCode) {
    if (owners().remove(normalizedCode) != null) {
      storage.store(owners());
    }
  }

  @Override
  public boolean isEmpty() {
    return owners().isEmpty();
  }

  @Override
  public java.util.Set<String> codesOwnedBy(Long userId) {
    return owners().entrySet().stream()
        .filter(e -> userId.equals(e.getValue()))
        .map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
