package com.svenruppert.urlshortener.api.security.bootstrap;

import eu.jsentinel.jcustos.persistence.eclipsestore.EclipseStoreJCustosStorage;
import eu.jsentinel.jcustos.persistence.eclipsestore.JCustosStorageFactory;
import eu.jsentinel.jcustos.persistence.eclipsestore.JCustosStoragePair;
import com.svenruppert.urlshortener.api.security.storage.AppStoragePaths;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * Lazy singleton holder for the {@link JCustosStoragePair} carrying
 * both the jCustos framework storage (audit, sessions, bootstrap
 * state, …) and the application's own user-directory storage manager
 * under one parent directory ({@link AppStoragePaths#baseDir()}).
 *
 * <p>The first call to {@link #pair()} opens the pair and registers a
 * single JVM shutdown hook running the pair's two-phase close.
 */
public final class JCustosStorageProvider {

  private static volatile JCustosStoragePair current;

  private JCustosStorageProvider() {
  }

  public static JCustosStoragePair pair() {
    JCustosStoragePair local = current;
    if (local != null) return local;
    synchronized (JCustosStorageProvider.class) {
      if (current == null) {
        current = JCustosStorageFactory.openAt(AppStoragePaths.baseDir());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          JCustosStoragePair live = current;
          if (live != null) {
            live.close();
          }
        }, "jsentinel-storage-pair-shutdown"));
      }
      return current;
    }
  }

  /** Framework storage: audit / sessions / bootstrap state. */
  public static EclipseStoreJCustosStorage framework() {
    return pair().framework();
  }

  /** Application storage manager (user directory). */
  public static EmbeddedStorageManager app() {
    return pair().app();
  }

  /** Test seam — install a custom pair before any production use. */
  public static synchronized void setPair(JCustosStoragePair replacement) {
    current = replacement;
  }
}
