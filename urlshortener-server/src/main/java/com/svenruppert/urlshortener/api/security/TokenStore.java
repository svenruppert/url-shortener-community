package com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.core.SecurityProfile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Bearer-token store — in-memory. Each successful login generates a
 * fresh UUID and stores the mapping {@code token → (user, createdAt)};
 * logout removes it.
 *
 * <p>Session timeout (P130): tokens carry an absolute max age derived from
 * the {@link SecurityProfile} (PRODUCTION 8h, STRICT 1h, DEVELOPMENT no
 * expiry so local runs/tests are unaffected). Expired tokens are dropped
 * lazily on {@link #resolve} / {@link #versionSnapshot}, so a stale token
 * becomes a 401 on the next request.
 */
public final class TokenStore {

  /** Singleton — one map per JVM. */
  public static final TokenStore INSTANCE = new TokenStore();

  private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();
  private final Clock clock;
  private final Duration maxAge; // null == no expiry

  private TokenStore() {
    this(maxAgeForProfile(), Clock.systemUTC());
  }

  /** Test/DI seam: explicit max age (nullable = no expiry) and clock. */
  public TokenStore(Duration maxAge, Clock clock) {
    this.maxAge = maxAge;
    this.clock = Objects.requireNonNullElse(clock, Clock.systemUTC());
  }

  public String issue(ApiUser user) {
    return issue(user, 0L);
  }

  /**
   * Issues a token capturing the subject's current jCustos version
   * ({@code versionSnapshot}) so drift detection can reject the token
   * once an admin bumps that version (role change).
   */
  public String issue(ApiUser user, long versionSnapshot) {
    Objects.requireNonNull(user, "user");
    String token = UUID.randomUUID().toString();
    tokens.put(token, new Entry(user, Instant.now(clock), versionSnapshot));
    return token;
  }

  public Optional<ApiUser> resolve(String token) {
    Entry entry = liveEntry(token);
    return entry == null ? Optional.empty() : Optional.of(entry.user);
  }

  /** The version snapshot captured when this token was issued. */
  public Optional<Long> versionSnapshot(String token) {
    Entry entry = liveEntry(token);
    return entry == null ? Optional.empty() : Optional.of(entry.versionSnapshot);
  }

  /** Looks up a non-expired entry, dropping it if it has timed out. */
  private Entry liveEntry(String token) {
    if (token == null) return null;
    Entry entry = tokens.get(token);
    if (entry == null) return null;
    if (isExpired(entry.createdAt, maxAge, Instant.now(clock))) {
      tokens.remove(token);
      return null;
    }
    return entry;
  }

  public void revoke(String token) {
    if (token != null) tokens.remove(token);
  }

  /** Revokes every token of the given user (user deletion cascade). */
  public void revokeAllOf(Long userId) {
    if (userId == null) return;
    tokens.entrySet().removeIf(e -> userId.equals(e.getValue().user.id()));
  }

  public Stream<TokenInfo> all() {
    return tokens.entrySet().stream()
        .map(e -> new TokenInfo(e.getKey(), e.getValue().user, e.getValue().createdAt))
        .toList()
        .stream();
  }

  /** Pure expiry check: a token created at {@code createdAt} is expired once
   * {@code now} is past {@code createdAt + maxAge}. A {@code null} maxAge never expires. */
  public static boolean isExpired(Instant createdAt, Duration maxAge, Instant now) {
    return maxAge != null && now.isAfter(createdAt.plus(maxAge));
  }

  private static Duration maxAgeForProfile() {
    return switch (SecurityProfile.current()) {
      case PRODUCTION -> Duration.ofHours(8);
      case STRICT -> Duration.ofHours(1);
      case DEVELOPMENT -> null;
    };
  }

  /** Public projection of a token entry for the admin sessions endpoint. */
  public record TokenInfo(String token, ApiUser user, Instant createdAt) {
  }

  private record Entry(ApiUser user, Instant createdAt, long versionSnapshot) {
  }
}
