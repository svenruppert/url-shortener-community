package com.svenruppert.urlshortener.api.security.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.bootstrap.AdministratorAccountStore;
import com.svenruppert.jsentinel.bootstrap.NewAdministrator;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.UserDirectory;
import com.svenruppert.urlshortener.api.security.roles.AuthorizationRole;

import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapter from {@link AdministratorAccountStore} (the bootstrap-flow
 * SPI) to the application's {@link UserDirectory}. The password in
 * {@link NewAdministrator} is already hashed by the bootstrap service,
 * so this calls {@link UserDirectory#registerWithHashedPassword}.
 *
 * <p>Logs at {@code INFO}/{@code ERROR} around the write because
 * jSentinel-Core's {@code InitialAdminBootstrapService} converts any
 * blanket {@code RuntimeException} into a cause-less
 * {@code InternalError}; without this local logging the operator
 * cannot diagnose persistence failures.
 */
public final class AdministratorAccountStoreImpl
    implements AdministratorAccountStore, HasLogger {

  private final UserDirectory directory;
  private final AtomicLong idSequence = new AtomicLong(1000);

  public AdministratorAccountStoreImpl(UserDirectory directory) {
    this.directory = Objects.requireNonNull(directory, "directory");
  }

  @Override
  public boolean hasAnyAdministrator() {
    return directory.hasAnyAdministrator();
  }

  @Override
  public void createAdministrator(NewAdministrator newAdministrator) {
    String displayName = newAdministrator.displayName() == null
        || newAdministrator.displayName().isBlank()
        ? newAdministrator.username()
        : newAdministrator.displayName();
    ApiUser user = new ApiUser(
        idSequence.getAndIncrement(),
        displayName,
        EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER));
    logger().info("Persisting initial administrator: username='{}', id={}, roles={}",
        newAdministrator.username(), user.id(), user.roles());
    try {
      directory.registerWithHashedPassword(
          newAdministrator.username(),
          newAdministrator.passwordHash(),
          user);
      logger().info("Initial administrator '{}' (id={}) committed to {}",
          newAdministrator.username(), user.id(),
          directory.getClass().getSimpleName());
    } catch (RuntimeException failure) {
      logger().error("Failed to persist initial administrator '{}' (id={}): {}",
          newAdministrator.username(), user.id(), failure.toString(), failure);
      throw failure;
    }
  }
}
