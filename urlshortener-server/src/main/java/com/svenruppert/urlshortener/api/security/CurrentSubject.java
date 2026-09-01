package com.svenruppert.urlshortener.api.security;

import com.sun.net.httpserver.HttpExchange;
import eu.jsentinel.jcustos.authorization.api.JCustosSubject;

import java.util.Optional;

/**
 * Per-exchange subject transport: {@link SecurityHttpHandler} stores
 * the resolved subject as an exchange attribute; downstream handlers
 * read it for owner-model decisions without re-resolving the token.
 */
public final class CurrentSubject {

  static final String ATTRIBUTE = "jsentinel.subject";

  private CurrentSubject() {
  }

  static void set(HttpExchange exchange, JCustosSubject subject) {
    exchange.setAttribute(ATTRIBUTE, subject);
  }

  public static Optional<JCustosSubject> of(HttpExchange exchange) {
    Object value = exchange.getAttribute(ATTRIBUTE);
    return value instanceof JCustosSubject subject
        ? Optional.of(subject)
        : Optional.empty();
  }
}
