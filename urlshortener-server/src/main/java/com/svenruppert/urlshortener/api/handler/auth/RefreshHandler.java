package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.TokenStore;
import com.svenruppert.urlshortener.api.security.RefreshTokenStore;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.security.services.VersionBumper;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.auth.AccessTokenResponse;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * POST /api/refresh — exchanges a valid refresh token for a fresh short-lived
 * access token (P150). Public (the refresh token is the credential). The new
 * access token captures the subject's current version for drift detection.
 *
 * <p>Body: {@code {"refreshToken":"…"}}
 */
public final class RefreshHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePost(ex)) return;
    Map<String, String> body = JsonUtils.parseJson(readBody(ex.getRequestBody()));
    String refreshToken = body == null ? null : body.get("refreshToken");
    if (isNullOrBlank(refreshToken)) {
      ErrorResponses.badRequest(ex, "refreshToken is required");
      return;
    }
    Optional<Long> userId = RefreshTokenStore.INSTANCE.resolveUser(refreshToken);
    if (userId.isEmpty()) {
      ErrorResponses.unauthorized(ex);
      return;
    }
    Optional<ApiUser> user = UserDirectoryProvider.directory().findById(userId.get());
    if (user.isEmpty()) {
      ErrorResponses.unauthorized(ex);
      return;
    }
    String access = TokenStore.INSTANCE.issue(user.get(), VersionBumper.current(user.get()));
    logger().info("access token refreshed for user id={}", userId.get());
    SuccessResponses.okJson(ex, JacksonJson.toJson(new AccessTokenResponse(access)));
  }
}
