package com.svenruppert.urlshortener.api.security;

import eu.jsentinel.jcustos.credential.history.InMemoryPasswordHistoryStore;
import eu.jsentinel.jcustos.credential.history.PasswordHistoryDecision;
import eu.jsentinel.jcustos.credential.history.PasswordHistoryPolicy;
import eu.jsentinel.jcustos.credential.history.PasswordHistoryService;
import eu.jsentinel.jcustos.credential.secret.SecretValue;

import java.time.Clock;
import java.time.Instant;

/**
 * Prevents reuse of recently used passwords (P190). Backed by jCustos's
 * {@link PasswordHistoryService}; the default policy retains the last 5.
 * Consulted by {@code MePasswordHandler} on a password change: a candidate
 * matching a stored (hashed) previous password is rejected, and each new
 * password is recorded.
 */
public final class PasswordHistoryGuard {

  public static final PasswordHistoryGuard INSTANCE =
      new PasswordHistoryGuard(PasswordHistoryPolicy.retain(5));

  private final PasswordHistoryService service;
  private final PasswordHistoryPolicy policy;

  public PasswordHistoryGuard(PasswordHistoryPolicy policy) {
    this.policy = policy;
    this.service = new PasswordHistoryService(new InMemoryPasswordHistoryStore(), AppPasswordHashing.SERVICE);
  }

  /** @return {@code true} if {@code newPassword} matches a retained previous password. */
  public boolean isReuse(String userId, String newPassword) {
    try (SecretValue candidate = SecretValue.ofString(newPassword)) {
      return service.evaluate(userId, candidate, policy) instanceof PasswordHistoryDecision.Reused;
    }
  }

  /** Records a newly set password (as a hash) in the user's history. */
  public void record(String userId, String newPassword) {
    String encodedHash = AppPasswordHashing.hasher().hash(newPassword.toCharArray());
    service.recordNew(userId, encodedHash, Instant.now(Clock.systemUTC()), policy);
  }
}
