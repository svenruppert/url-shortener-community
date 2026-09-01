package com.svenruppert.urlshortener.api.security.model;

import java.util.Map;

/**
 * Persistence seam for {@link PersistentUserDirectory}. The directory
 * asks for a snapshot at construction and pushes the updated snapshot
 * on every mutation.
 *
 * <ul>
 *   <li>{@link EclipseStoreUserDirectoryPersistence} — default,
 *       persistent, shares the app-side manager of the
 *       {@code JCustosStoragePair}.</li>
 *   <li>{@link InMemoryUserDirectoryPersistence} — test seam, no disk
 *       side-effect.</li>
 * </ul>
 *
 * <p>Future backends (JDBC, LDAP, IAM) plug in through this contract
 * without touching the directory.
 */
public interface UserDirectoryPersistence extends AutoCloseable {

  /** Loads the current snapshot (keyed by username); never {@code null}. */
  Map<String, StoredUser> load();

  /** Persists the snapshot atomically. */
  void save(Map<String, StoredUser> snapshot);

  /** Releases backend resources. Idempotent. */
  @Override
  void close();
}
