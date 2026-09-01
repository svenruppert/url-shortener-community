package com.svenruppert.urlshortener.api.security.model;

import com.svenruppert.dependencies.core.logger.HasLogger;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Eclipse-Store-backed {@link UserDirectoryPersistence}. Shares the
 * app-side {@code EmbeddedStorageManager} of the
 * {@code JCustosStoragePair}; the pair owns the lifecycle, this
 * class only owns the root structure and the read/write methods.
 *
 * <p>Eclipse-Store's type-mapping handles record-header evolution:
 * adding / renaming / reordering an {@link ApiUser} or
 * {@link StoredUser} field does not fail on reload the way Java
 * serialization would.
 */
public final class EclipseStoreUserDirectoryPersistence
    implements UserDirectoryPersistence, HasLogger {

  /** Storage root holding the {@code byUsername} map. */
  public static final class AppUsersRoot {
    public Map<String, StoredUser> byUsername = new LinkedHashMap<>();
  }

  private final EmbeddedStorageManager manager;
  private final AppUsersRoot root;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public EclipseStoreUserDirectoryPersistence(EmbeddedStorageManager manager) {
    this.manager = Objects.requireNonNull(manager, "manager");
    Object existing = manager.root();
    if (existing instanceof AppUsersRoot loaded) {
      this.root = loaded;
    } else {
      this.root = new AppUsersRoot();
      manager.setRoot(this.root);
      manager.storeRoot();
    }
    logger().info("EclipseStoreUserDirectoryPersistence ready ({} user(s) loaded)",
        root.byUsername.size());
  }

  @Override
  public Map<String, StoredUser> load() {
    return new LinkedHashMap<>(root.byUsername);
  }

  @Override
  public void save(Map<String, StoredUser> snapshot) {
    if (closed.get()) {
      throw new IllegalStateException("persistence is closed");
    }
    root.byUsername.clear();
    root.byUsername.putAll(snapshot);
    manager.store(root.byUsername);
  }

  /**
   * Marks this persistence closed. The manager is NOT shut down here
   * — the {@code JCustosStoragePair} owns the storage lifecycle.
   */
  @Override
  public void close() {
    closed.compareAndSet(false, true);
  }
}
