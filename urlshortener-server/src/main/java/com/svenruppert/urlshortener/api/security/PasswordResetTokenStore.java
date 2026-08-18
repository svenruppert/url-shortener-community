package com.svenruppert.urlshortener.api.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory password-reset token store (P180). Single-use, short-lived tokens
 * that map to a user; issued on a reset request and consumed on confirm.
 */
public final class PasswordResetTokenStore {

  public static final PasswordResetTokenStore INSTANCE =
      new PasswordResetTokenStore(Duration.ofMinutes(15), Clock.systemUTC());

  private static final String PREFIX = "prt_";
  private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();
  private final Duration ttl;
  private final Clock clock;

  public PasswordResetTokenStore(Duration ttl, Clock clock) {
    this.ttl = ttl;
    this.clock = Objects.requireNonNullElse(clock, Clock.systemUTC());
  }

  public String issue(Long userId) {
    String token = PREFIX + UUID.randomUUID().toString().replace("-", "");
    tokens.put(token, new Entry(userId, Instant.now(clock)));
    return token;
  }

  /** Consumes a token (single-use): returns the user id if valid, and removes it. */
  public Optional<Long> consume(String token) {
    if (token == null) return Optional.empty();
    Entry e = tokens.remove(token);
    if (e == null) return Optional.empty();
    if (TokenStore.isExpired(e.createdAt, ttl, Instant.now(clock))) {
      return Optional.empty();
    }
    return Optional.of(e.userId);
  }

  private record Entry(Long userId, Instant createdAt) {
  }
}
