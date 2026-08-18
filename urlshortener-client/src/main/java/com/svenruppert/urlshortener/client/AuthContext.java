package com.svenruppert.urlshortener.client;

/**
 * Process-wide default Bearer token used by every client wrapper
 * whose token supplier was not overridden.
 *
 * <p>CLI tools and tests log in once and call
 * {@link #setGlobalToken(String)}. Multi-session consumers (the
 * Vaadin UI) must NOT use the global token — they pass a
 * session-scoped supplier via {@code withBearerToken(...)} on each
 * client instead.
 */
public final class AuthContext {

  private static volatile String globalToken;

  private AuthContext() {
  }

  public static String getGlobalToken() {
    return globalToken;
  }

  public static void setGlobalToken(String token) {
    globalToken = token;
  }

  public static void clear() {
    globalToken = null;
  }
}
