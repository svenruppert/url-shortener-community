package com.svenruppert.urlshortener.api.security;

import eu.jsentinel.jcustos.bruteforce.InMemoryLoginAttemptPolicy;
import eu.jsentinel.jcustos.bruteforce.LoginAttemptPolicy;

/**
 * Process-wide brute-force lockout policy for the login endpoint (P110).
 * Backed by jCustos's in-memory {@link LoginAttemptPolicy}: after a
 * threshold of failures per (user, IP) within a window, further attempts are
 * locked out for a growing duration. {@code LoginHandler} consults it before
 * checking credentials and records success/failure.
 */
public final class LoginAttemptGuard {

  public static final LoginAttemptPolicy INSTANCE = new InMemoryLoginAttemptPolicy();

  private LoginAttemptGuard() {
  }
}
