package com.svenruppert.urlshortener.api.security.bootstrap;

import eu.jsentinel.jcustos.bootstrap.BootstrapConfiguration;
import eu.jsentinel.jcustos.bootstrap.BootstrapMode;
import eu.jsentinel.jcustos.bootstrap.BootstrapStartup;
import eu.jsentinel.jcustos.bootstrap.BootstrapStateService;
import eu.jsentinel.jcustos.bootstrap.BootstrapTokenGenerator;
import eu.jsentinel.jcustos.bootstrap.BootstrapTokenOutput;
import eu.jsentinel.jcustos.bootstrap.BootstrapTokenStore;
import eu.jsentinel.jcustos.bootstrap.FileBootstrapTokenOutput;
import eu.jsentinel.jcustos.bootstrap.FileBootstrapTokenStore;
import eu.jsentinel.jcustos.bootstrap.InitialAdminBootstrapService;
import eu.jsentinel.jcustos.bootstrap.MinimumLengthPasswordPolicy;
import com.svenruppert.urlshortener.api.security.AppPasswordHashing;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.security.storage.AppStoragePaths;

import java.nio.file.Path;
import java.time.Clock;
import com.svenruppert.dependencies.core.logger.HasLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

/**
 * Wires the first-admin bootstrap flow (PRE-REBUILD-SCOPE Kap. 2.8).
 *
 * <p>Lazy singleton: the first {@link #instance()} constructs the
 * chain once and caches it.
 *
 * <ol>
 *   <li>{@link BootstrapStateService} reads
 *       {@code AdministratorAccountStore.hasAnyAdministrator()} to
 *       decide whether the system is uninitialised.</li>
 *   <li>{@link BootstrapStartup#initializeIfRequired} generates a
 *       one-time token on first start, persists it to the token file
 *       and prints path + token to stdout.</li>
 *   <li>{@link InitialAdminBootstrapService} validates the token from
 *       the {@code POST /api/setup} form and creates the admin via the
 *       {@link AdministratorAccountStoreImpl} adapter.</li>
 * </ol>
 *
 * <p>Delete the token file (and any admin in storage) to force a
 * re-bootstrap.
 */
public final class BootstrapWiring {

  public static final BootstrapMode DEFAULT_MODE = BootstrapMode.PERSISTENT_FILE;
  public static final Path DEFAULT_TOKEN_FILE = AppStoragePaths.bootstrapTokenFile();

  private static volatile BootstrapWiring current;

  private final BootstrapStateService stateService;
  private final InitialAdminBootstrapService bootstrapService;

  private BootstrapWiring(BootstrapStateService stateService,
                          InitialAdminBootstrapService bootstrapService) {
    this.stateService = stateService;
    this.bootstrapService = bootstrapService;
  }

  public BootstrapStateService stateService() {
    return stateService;
  }

  public InitialAdminBootstrapService bootstrapService() {
    return bootstrapService;
  }

  public static BootstrapWiring instance() {
    BootstrapWiring local = current;
    if (local != null) return local;
    synchronized (BootstrapWiring.class) {
      if (current == null) current = build();
      return current;
    }
  }

  private static BootstrapWiring build() {
    BootstrapConfiguration config = new BootstrapConfiguration(
        DEFAULT_MODE, DEFAULT_TOKEN_FILE, BootstrapConfiguration.DEFAULT_VALIDITY);
    AdministratorAccountStoreImpl adminStore =
        new AdministratorAccountStoreImpl(UserDirectoryProvider.directory());
    BootstrapStateService state = new BootstrapStateService(adminStore, config.mode());
    BootstrapTokenStore tokenStore = new FileBootstrapTokenStore(config.tokenFilePath());
    BootstrapTokenOutput output = new FileBootstrapTokenOutput();
    BootstrapStartup.initializeIfRequired(
        state, tokenStore, new BootstrapTokenGenerator(), output, config);
    enforceOwnerOnlyPermissions(config.tokenFilePath());
    // Hash the initial admin's password with the SAME service the user
    // directory verifies logins against (AppPasswordHashing.SERVICE) —
    // otherwise the bootstrap hash never cross-verifies with the login
    // path's hasher.
    InitialAdminBootstrapService service = new InitialAdminBootstrapService(
        state, tokenStore, adminStore,
        AppPasswordHashing.SERVICE,
        new MinimumLengthPasswordPolicy(8),
        config.tokenValidity(), Clock.systemUTC());
    return new BootstrapWiring(state, service);
  }

  /**
   * SEC-L7 (V00.17.00 P080): the bootstrap token is a one-time admin
   * credential — the file must be readable by the service account only
   * ({@code rw-------}). Enforced here because the jCustos token store does
   * not guarantee it; skipped on file systems without POSIX permissions.
   */
  public static void enforceOwnerOnlyPermissions(Path tokenFile) {
    try {
      if (tokenFile == null || !Files.exists(tokenFile)) {
        return;
      }
      var view = Files.getFileAttributeView(
          tokenFile, PosixFileAttributeView.class);
      if (view == null) {
        return; // non-POSIX file system (e.g. Windows ACLs)
      }
      view.setPermissions(EnumSet.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE));
    } catch (IOException e) {
      HasLogger.staticLogger()
          .warn("could not restrict bootstrap-token file permissions: {}", e.getMessage());
    }
  }
}
