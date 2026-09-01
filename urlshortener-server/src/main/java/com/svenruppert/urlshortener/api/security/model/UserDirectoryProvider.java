package com.svenruppert.urlshortener.api.security.model;

import com.svenruppert.urlshortener.api.security.AppPasswordHashing;
import com.svenruppert.urlshortener.api.security.bootstrap.JCustosStorageProvider;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Static holder for the {@link UserDirectory} singleton.
 *
 * <p>Production default (Stage E): an
 * {@link EclipseStoreUserDirectoryPersistence}-backed
 * {@link PersistentUserDirectory} — empty on first start, populated
 * via the token-based first-admin bootstrap. Built lazily through the
 * <a href="https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom">
 * Initialization-on-Demand Holder</a> idiom so merely loading this
 * class (static analysis, a PIT worker) does NOT open Eclipse-Store.
 *
 * <p>Test seam: install a supplier via
 * {@link #setDirectorySupplier(Supplier)} <em>before</em> any
 * production call to {@link #directory()}. The supplier is invoked
 * lazily (so it can build a directory that needs the post-bootstrap
 * hashing service) and cached. Tests install
 * {@code InMemoryUserDirectory::new} to keep the seeded admin/user
 * without touching the persistent store or the bootstrap flow.
 */
public final class UserDirectoryProvider {

  private static volatile Supplier<UserDirectory> overrideSupplier;
  private static volatile UserDirectory override;

  private UserDirectoryProvider() {
  }

  /** Holder — initialised on first read, not at class load. */
  private static final class Holder {
    static final UserDirectory INSTANCE = buildDefault();
  }

  public static UserDirectory directory() {
    Supplier<UserDirectory> supplier = overrideSupplier;
    if (supplier != null) {
      UserDirectory cached = override;
      if (cached == null) {
        synchronized (UserDirectoryProvider.class) {
          if (override == null) {
            override = supplier.get();
          }
          cached = override;
        }
      }
      return cached;
    }
    return Holder.INSTANCE;
  }

  /** Test seam — install a lazily-built directory (wins over the holder). */
  public static void setDirectorySupplier(Supplier<UserDirectory> supplier) {
    overrideSupplier = Objects.requireNonNull(supplier, "supplier");
    override = null;
  }

  /** Test seam — install an already-built directory (wins over the holder). */
  public static void setDirectory(UserDirectory replacement) {
    Objects.requireNonNull(replacement, "replacement");
    overrideSupplier = () -> replacement;
    override = replacement;
  }

  /** Test seam — clear any override; next call returns the persistent default. */
  public static void reset() {
    overrideSupplier = null;
    override = null;
  }

  private static UserDirectory buildDefault() {
    UserDirectoryPersistence persistence =
        new EclipseStoreUserDirectoryPersistence(JCustosStorageProvider.app());
    return new PersistentUserDirectory(persistence, AppPasswordHashing.hasher());
  }
}
