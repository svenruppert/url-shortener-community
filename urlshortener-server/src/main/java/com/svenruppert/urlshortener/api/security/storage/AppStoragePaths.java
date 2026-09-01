package com.svenruppert.urlshortener.api.security.storage;

import eu.jsentinel.jcustos.persistence.eclipsestore.StorageLayout;

import java.nio.file.Path;

/**
 * Single source of truth for the jCustos storage base directory.
 *
 * <p>Production defaults to {@code ./data/jsentinel-rest}. Tests and
 * any deployment redirect it via {@code -Dapp.storage.dir=/some/path}
 * (Surefire sets it to {@code target/test-data} so no test run creates
 * the repo-rooted tree and {@code mvn clean} resets all state).
 *
 * <p>Independent of the URL-mapping EclipseStore under
 * {@code data/urlshortener} — different directories, different
 * {@code EmbeddedStorageManager}s, both valid within one JVM.
 */
public final class AppStoragePaths {

  /** System-property name for the storage base directory. */
  public static final String PROPERTY = "app.storage.dir";

  /** Built-in default when nothing was configured. */
  public static final String DEFAULT = "./data/jsentinel-rest";

  private AppStoragePaths() {
  }

  /** Base directory the {@code JCustosStoragePair} is opened under. */
  public static Path baseDir() {
    return Path.of(System.getProperty(PROPERTY, DEFAULT));
  }

  /** One-time bootstrap-token file, inside the framework sub-directory. */
  public static Path bootstrapTokenFile() {
    return baseDir()
        .resolve(StorageLayout.DEFAULT.frameworkSubdir())
        .resolve("bootstrap.token");
  }
}
