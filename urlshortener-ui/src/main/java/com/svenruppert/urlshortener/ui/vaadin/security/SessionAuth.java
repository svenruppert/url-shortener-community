package com.svenruppert.urlshortener.ui.vaadin.security;

import com.vaadin.flow.server.VaadinSession;

/**
 * Session-scoped holder for the Bearer token obtained from the REST
 * backend at login. The client factories read {@link #currentToken()}
 * so every REST call carries the logged-in user's token — one token
 * per Vaadin session, never a process-wide global (multiple users may
 * share the JVM).
 */
public final class SessionAuth {

  private static final String TOKEN_ATTRIBUTE = "urlshortener.bearerToken";

  private SessionAuth() {
  }

  /** Stores the Bearer token in the current Vaadin session. */
  public static void setToken(String token) {
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) {
      session.setAttribute(TOKEN_ATTRIBUTE, token);
    }
  }

  /** @return the current session's Bearer token, or {@code null} if not logged in. */
  public static String currentToken() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session == null) {
      return null;
    }
    Object value = session.getAttribute(TOKEN_ATTRIBUTE);
    return value instanceof String token ? token : null;
  }

  /** Removes the token (logout). */
  public static void clear() {
    VaadinSession session = VaadinSession.getCurrent();
    if (session != null) {
      session.setAttribute(TOKEN_ATTRIBUTE, null);
    }
  }
}
