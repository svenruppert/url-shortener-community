package com.svenruppert.urlshortener.api.handler.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.security.TokenStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_SESSIONS;

/**
 * Admin session management (P240):
 * <ul>
 *   <li>{@code GET /api/admin/sessions} — active sessions (token masked,
 *       user, created-at)</li>
 *   <li>{@code DELETE /api/admin/sessions/{userId}} — revoke every session of
 *       a user</li>
 * </ul>
 * Admin-only (reuses the {@code storeInfo} permission). Tokens are never
 * returned in full; revocation is keyed by user id, so a leaked list cannot be
 * replayed.
 */
@RequiresPermission("storeInfo")
public final class SessionsHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    String method = ex.getRequestMethod();
    String path = ex.getRequestURI().getPath();

    if ("GET".equalsIgnoreCase(method) && PATH_ADMIN_SESSIONS.equals(path)) {
      list(ex);
      return;
    }
    if ("DELETE".equalsIgnoreCase(method) && path.startsWith(PATH_ADMIN_SESSIONS + "/")) {
      String idPart = path.substring((PATH_ADMIN_SESSIONS + "/").length());
      Long userId = parseId(idPart);
      if (userId == null) {
        ErrorResponses.badRequest(ex, "expected DELETE /api/admin/sessions/{userId}");
        return;
      }
      TokenStore.INSTANCE.revokeAllOf(userId);
      logger().info("admin revoked all sessions of user id={}", userId);
      ex.sendResponseHeaders(HttpStatus.NO_CONTENT.code(), -1);
      return;
    }
    ErrorResponses.unmatchedRoute(ex, "GET, DELETE", "unknown sessions route");
  }

  private void list(HttpExchange ex)
      throws IOException {
    List<Map<String, Object>> sessions = TokenStore.INSTANCE.all()
        .map(t -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("token", mask(t.token()));
          m.put("userId", t.user().id());
          m.put("user", t.user().name());
          m.put("createdAt", t.createdAt().toString());
          return m;
        })
        .collect(Collectors.toList());
    SuccessResponses.okJson(ex, JacksonJson.toJson(sessions));
  }

  private static String mask(String token) {
    return token == null || token.length() <= 8 ? "****" : token.substring(0, 8) + "…";
  }

  private static Long parseId(String s) {
    try {
      return s == null || s.isBlank() ? null : Long.valueOf(s.trim());
    } catch (NumberFormatException notNumeric) {
      return null;
    }
  }
}
