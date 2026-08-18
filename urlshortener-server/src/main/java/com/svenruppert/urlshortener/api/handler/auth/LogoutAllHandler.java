package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.jsentinel.authorization.api.JSentinelSubject;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.urlshortener.api.security.TokenStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;

import java.io.IOException;
import java.util.Optional;

/**
 * POST /api/logout/all — global logout (P200): revokes every Bearer token
 * of the currently authenticated subject, not just the one presented. Any
 * open session for the user becomes a 401 on its next request. Authenticated
 * only (no specific permission); the subject is supplied by
 * {@link com.svenruppert.urlshortener.api.security.SecurityHttpHandler}.
 */
public final class LogoutAllHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePost(ex)) return;

    Optional<JSentinelSubject> subject = CurrentSubject.of(ex);
    if (subject.isEmpty()) {
      ErrorResponses.unauthorized(ex);
      return;
    }
    Long id = parseId(subject.get().subjectId());
    if (id != null) {
      TokenStore.INSTANCE.revokeAllOf(id);
      logger().info("global logout for subject id={}", id);
    }
    ex.sendResponseHeaders(HttpStatus.NO_CONTENT.code(), -1);
  }

  private static Long parseId(String s) {
    try {
      return s == null ? null : Long.parseLong(s.trim());
    } catch (NumberFormatException notNumeric) {
      return null;
    }
  }
}
