package com.svenruppert.urlshortener.api.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory refresh-token store (P150). Refresh tokens are long-lived and are
 * exchanged at {@code POST /api/refresh} for a fresh short-lived access token,
 * so access tokens can carry a tight timeout without forcing frequent logins.
 */
public final class RefreshTokenStore {

  public static final RefreshTokenStore INSTANCE =
      new RefreshTokenStore(Duration.ofDays(30), Clock.systemUTC());

  private static final String PREFIX = "rt_";
  private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();
  private final Duration ttl;
  private final Clock clock;

  public RefreshTokenStore(Duration ttl, Clock clock) {
    this.ttl = ttl;
    this.clock = Objects.requireNonNullElse(clock, Clock.systemUTC());
  }

  public String issue(Long userId) {
    String token = PREFIX + UUID.randomUUID().toString().replace("-", "");
    tokens.put(token, new Entry(userId, Instant.now(clock)));
    return token;
  }

  public Optional<Long> resolveUser(String refreshToken) {
    if (refreshToken == null) return Optional.empty();
    Entry e = tokens.get(refreshToken);
    if (e == null) return Optional.empty();
    if (TokenStore.isExpired(e.createdAt, ttl, Instant.now(clock))) {
      tokens.remove(refreshToken);
      return Optional.empty();
    }
    return Optional.of(e.userId);
  }

  public void revoke(String refreshToken) {
    if (refreshToken != null) tokens.remove(refreshToken);
  }

  public void revokeAllOf(Long userId) {
    if (userId == null) return;
    tokens.entrySet().removeIf(en -> userId.equals(en.getValue().userId));
  }

  private record Entry(Long userId, Instant createdAt) {
  }
}
