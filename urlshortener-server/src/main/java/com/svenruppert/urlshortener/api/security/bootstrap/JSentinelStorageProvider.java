package com.svenruppert.urlshortener.api.security.bootstrap;

import com.svenruppert.jsentinel.persistence.eclipsestore.EclipseStoreJSentinelStorage;
import com.svenruppert.jsentinel.persistence.eclipsestore.JSentinelStorageFactory;
import com.svenruppert.jsentinel.persistence.eclipsestore.JSentinelStoragePair;
import com.svenruppert.urlshortener.api.security.storage.AppStoragePaths;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * Lazy singleton holder for the {@link JSentinelStoragePair} carrying
 * both the jSentinel framework storage (audit, sessions, bootstrap
 * state, …) and the application's own user-directory storage manager
 * under one parent directory ({@link AppStoragePaths#baseDir()}).
 *
 * <p>The first call to {@link #pair()} opens the pair and registers a
 * single JVM shutdown hook running the pair's two-phase close.
 */
public final class JSentinelStorageProvider {

  private static volatile JSentinelStoragePair current;

  private JSentinelStorageProvider() {
  }

  public static JSentinelStoragePair pair() {
    JSentinelStoragePair local = current;
    if (local != null) return local;
    synchronized (JSentinelStorageProvider.class) {
      if (current == null) {
        current = JSentinelStorageFactory.openAt(AppStoragePaths.baseDir());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          JSentinelStoragePair live = current;
          if (live != null) {
            live.close();
          }
        }, "jsentinel-storage-pair-shutdown"));
      }
      return current;
    }
  }

  /** Framework storage: audit / sessions / bootstrap state. */
  public static EclipseStoreJSentinelStorage framework() {
    return pair().framework();
  }

  /** Application storage manager (user directory). */
  public static EmbeddedStorageManager app() {
    return pair().app();
  }

  /** Test seam — install a custom pair before any production use. */
  public static synchronized void setPair(JSentinelStoragePair replacement) {
    current = replacement;
  }
}
