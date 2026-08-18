package com.svenruppert.urlshortener.api.security.model;

/**
 * Persisted user record — {@link ApiUser} plus the hashed password.
 *
 * <p>Top-level public record (no inner type) so Eclipse-Store's
 * type-mapping sees the same canonical class regardless of which
 * persistence backend reads / writes it. The hash format is opaque to
 * the persistence layer; it lives inside the {@code PasswordHasher}.
 */
public record StoredUser(ApiUser user, String passwordHash) {
}
