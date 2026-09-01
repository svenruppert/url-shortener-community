package com.svenruppert.urlshortener.core;

import java.util.Locale;

/**
 * Selects the jCustos bootstrap hardening profile (P100). Resolved from the
 * system property {@code app.security.profile} (or env {@code APP_SECURITY_PROFILE}),
 * defaulting to {@link #DEVELOPMENT} so tests and local runs are unaffected.
 * Production deployments set {@code -Dapp.security.profile=production} (or
 * {@code strict}).
 */
public enum SecurityProfile {
  DEVELOPMENT,
  PRODUCTION,
  STRICT;

  public static final String PROPERTY = "app.security.profile";
  public static final String ENV = "APP_SECURITY_PROFILE";

  public static SecurityProfile current() {
    String raw = System.getProperty(PROPERTY);
    if (raw == null || raw.isBlank()) raw = System.getenv(ENV);
    if (raw == null || raw.isBlank()) return DEVELOPMENT;
    try {
      return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      return DEVELOPMENT;
    }
  }
}
