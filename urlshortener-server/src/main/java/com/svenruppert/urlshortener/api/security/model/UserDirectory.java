package com.svenruppert.urlshortener.api.security.model;

import com.svenruppert.jsentinel.authentication.PasswordHasher;
import com.svenruppert.urlshortener.api.security.roles.AuthorizationRole;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Application user store. Decoupled from the concrete in-memory
 * implementation so a future backend swap (Eclipse-Store, DB, LDAP)
 * does not touch consumers.
 */
public interface UserDirectory {

  Optional<ApiUser> findByCredentials(Credentials credentials);

  default boolean checkCredentials(Credentials credentials) {
    return findByCredentials(credentials).isPresent();
  }

  Optional<ApiUser> findById(Long id);

  Optional<ApiUser> findByUsername(String username);

  Stream<ApiUser> all();

  void addUser(String username, String plaintextPassword, ApiUser user);

  /**
   * Adds a user whose password was already hashed elsewhere (typically
   * the bootstrap flow's {@code InitialAdminBootstrapService}). Bypasses
   * the directory's own hasher.
   */
  void registerWithHashedPassword(String username, String passwordHash, ApiUser user);

  void deleteUser(Long id);

  /** @return {@code true} if at least one user has the {@code ADMIN} role */
  boolean hasAnyAdministrator();

  void assignRole(Long id, AuthorizationRole role);

  void revokeRole(Long id, AuthorizationRole role);

  /** Replaces a user's password (self-service change / admin reset). */
  void updatePassword(Long id, String newPlaintextPassword);

  PasswordHasher passwordHasher();
}
