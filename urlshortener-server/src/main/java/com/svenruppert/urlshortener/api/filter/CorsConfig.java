package com.svenruppert.urlshortener.api.filter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CORS allow-list configuration (P230). Read from the system property
 * {@code app.cors.allowed-origins} (comma-separated). Empty by default, so with
 * no configuration the API stays locked down (every browser preflight blocked),
 * exactly as before — CORS is strictly opt-in per trusted origin.
 */
public final class CorsConfig {

  public static final String PROPERTY = "app.cors.allowed-origins";

  private CorsConfig() {
  }

  public static Set<String> allowedOrigins() {
    String raw = System.getProperty(PROPERTY, "");
    if (raw == null || raw.isBlank()) return Set.of();
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  public static boolean isAllowed(String origin) {
    return origin != null && allowedOrigins().contains(origin);
  }
}
