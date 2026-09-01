package com.svenruppert.urlshortener.api.extension;

import java.nio.file.Path;

/**
 * Storage-administration seam for plug-ins that need snapshot access to the
 * host's persistent app store (V00.18.00 P090): the profile flag, the real
 * app-store directory, and a consistent online full backup of the running
 * storage. Plug-ins reach the two jCustos security stores separately via
 * {@code JCustosStorageProvider}; this seam only covers what community
 * code keeps private.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface StorageAdmin {

  /** {@code true} when the server runs on the persistent EclipseStore profile. */
  boolean persistent();

  /** Absolute app-store directory (the running EclipseStore's home); {@code null} in-memory. */
  Path appDataDir();

  /**
   * Exports a consistent full backup of the running app store into the given
   * (empty or absent) target directory — the result is a bootable storage
   * directory.
   *
   * @throws IllegalStateException on the in-memory profile
   */
  void issueAppStoreBackup(Path targetDirectory);
}
