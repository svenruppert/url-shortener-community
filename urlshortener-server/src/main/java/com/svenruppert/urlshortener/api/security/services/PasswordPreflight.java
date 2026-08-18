package com.svenruppert.urlshortener.api.security.services;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.credential.compromised.CompromisedPasswordChecker;
import com.svenruppert.jsentinel.credential.compromised.CompromisedPasswordResult;
import com.svenruppert.jsentinel.credential.compromised.LocalBlocklistCompromisedPasswordChecker;
import com.svenruppert.jsentinel.credential.compromised.hibp.HaveIBeenPwnedCompromisedPasswordChecker;
import com.svenruppert.jsentinel.credential.secret.SecretValue;
import com.svenruppert.urlshortener.core.SecurityProfile;

import java.time.Duration;
import java.util.List;

/**
 * Password-acceptance check run before any new / changed password is committed.
 *
 * <p>Layer 1 — a local blocklist of the most common passwords: fast, offline,
 * always on. Layer 2 (P170) — an online HaveIBeenPwned k-anonymity query — runs
 * only under a non-development {@link SecurityProfile} (so the build and tests
 * stay offline) and is <em>fail-open</em>: if HIBP is unreachable the password
 * is not blocked on that basis.
 *
 * <p>A {@link CompromisedPasswordResult.Pwned} rejects; callers show a generic
 * message (never name the source — CWE-209).
 */
public final class PasswordPreflight
    implements HasLogger {

  private static final CompromisedPasswordChecker LOCAL =
      new LocalBlocklistCompromisedPasswordChecker(List.of(
          "password", "password1", "password123",
          "qwerty", "qwerty123", "letmein",
          "admin", "admin123", "administrator",
          "welcome", "welcome1",
          "12345678", "123456789", "abc12345",
          "iloveyou", "monkey", "dragon",
          "hunter2", "trustno1"));

  private static final CompromisedPasswordChecker HIBP =
      HaveIBeenPwnedCompromisedPasswordChecker.usingJdkHttpClient(
          HaveIBeenPwnedCompromisedPasswordChecker.DEFAULT_ENDPOINT, Duration.ofSeconds(3));

  private static final HasLogger LOG = new PasswordPreflight();

  private PasswordPreflight() {
  }

  /** @return {@code true} if the candidate is acceptable (not known-compromised). */
  public static boolean isAcceptable(String candidate) {
    if (candidate == null || candidate.isEmpty()) {
      return false;
    }
    try (SecretValue secret = SecretValue.ofString(candidate)) {
      if (LOCAL.check(secret) instanceof CompromisedPasswordResult.Pwned) {
        return false;
      }
    }
    // Layer 2: online HIBP — only in hardened profiles, fail-open on any error.
    if (SecurityProfile.current() != SecurityProfile.DEVELOPMENT) {
      try (SecretValue secret = SecretValue.ofString(candidate)) {
        if (HIBP.check(secret) instanceof CompromisedPasswordResult.Pwned) {
          return false;
        }
      } catch (RuntimeException hibpUnavailable) {
        LOG.logger().warn("HIBP check unavailable, failing open: {}", hibpUnavailable.toString());
      }
    }
    return true;
  }
}
