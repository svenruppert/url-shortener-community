package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.TokenStore;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;

import java.io.IOException;

import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;

/**
 * POST /api/logout — revokes the presented Bearer token. Idempotent:
 * an unknown or missing token still answers 200.
 */
public class LogoutHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePost(ex)) return;
    String auth = ex.getRequestHeaders().getFirst(AUTHORIZATION_HEADER);
    if (auth != null && auth.startsWith(BEARER_PREFIX)) {
      TokenStore.INSTANCE.revoke(auth.substring(BEARER_PREFIX.length()).trim());
      logger().info("bearer token revoked");
    }
    SuccessResponses.okJson(ex, "{\"ok\":true}");
  }
}
