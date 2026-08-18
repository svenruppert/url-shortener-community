package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.api.JSentinelSubject;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.urlshortener.api.security.RefreshTokenStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.auth.RefreshTokenResponse;

import java.io.IOException;
import java.util.Optional;

/**
 * POST /api/me/refresh-token — issues a long-lived refresh token for the
 * authenticated subject (P150), later exchanged at {@code /api/refresh}.
 */
public final class MeRefreshTokenHandler
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
    Long userId = parseId(subject.get().subjectId());
    if (userId == null) {
      ErrorResponses.unauthorized(ex);
      return;
    }
    String refreshToken = RefreshTokenStore.INSTANCE.issue(userId);
    logger().info("refresh token issued for user id={}", userId);
    SuccessResponses.okJson(ex, JacksonJson.toJson(new RefreshTokenResponse(refreshToken)));
  }

  private static Long parseId(String s) {
    try {
      return s == null ? null : Long.valueOf(s.trim());
    } catch (NumberFormatException notNumeric) {
      return null;
    }
  }
}
