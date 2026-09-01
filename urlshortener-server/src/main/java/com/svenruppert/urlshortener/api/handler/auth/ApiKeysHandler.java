package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import eu.jsentinel.jcustos.authorization.api.JCustosSubject;
import com.svenruppert.urlshortener.api.security.ApiKeyStore;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.JsonUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ME_API_KEYS;

/**
 * Self-service API-key management (P160), authenticated-only:
 * <ul>
 *   <li>{@code POST /api/me/api-keys} {"label"} — create a key (plaintext
 *       returned once)</li>
 *   <li>{@code GET /api/me/api-keys} — list own keys (id, label, createdAt)</li>
 *   <li>{@code DELETE /api/me/api-keys/{id}} — revoke own key</li>
 * </ul>
 * Keys authenticate subsequent requests via the {@code X-API-Key} header
 * (resolved in {@code SecurityHttpHandler}).
 */
public final class ApiKeysHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    Optional<JCustosSubject> subject = CurrentSubject.of(ex);
    if (subject.isEmpty()) {
      ErrorResponses.unauthorized(ex);
      return;
    }
    Long userId = parseId(subject.get().subjectId());
    if (userId == null) {
      ErrorResponses.unauthorized(ex);
      return;
    }

    String method = ex.getRequestMethod();
    String path = ex.getRequestURI().getPath();

    if ("POST".equalsIgnoreCase(method) && PATH_ME_API_KEYS.equals(path)) {
      Map<String, String> body = JsonUtils.parseJson(readBody(ex.getRequestBody()));
      String label = body == null ? null : body.get("label");
      String key = ApiKeyStore.INSTANCE.issue(userId, label);
      logger().info("api key issued for user id={}", userId);
      Map<String, Object> resp = new LinkedHashMap<>();
      resp.put("key", key);
      resp.put("label", label == null ? "" : label);
      SuccessResponses.withStatusJson(ex, HttpStatus.CREATED.code(), JacksonJson.toJson(resp));
      return;
    }
    if ("GET".equalsIgnoreCase(method) && PATH_ME_API_KEYS.equals(path)) {
      List<Map<String, Object>> keys = ApiKeyStore.INSTANCE.listFor(userId).stream()
          .map(k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", k.id());
            m.put("label", k.label());
            m.put("createdAt", k.createdAt().toString());
            return m;
          })
          .collect(Collectors.toList());
      SuccessResponses.okJson(ex, JacksonJson.toJson(keys));
      return;
    }
    if ("DELETE".equalsIgnoreCase(method) && path.startsWith(PATH_ME_API_KEYS + "/")) {
      String id = path.substring((PATH_ME_API_KEYS + "/").length());
      if (id.isBlank()) {
        ErrorResponses.badRequest(ex, "expected DELETE /api/me/api-keys/{id}");
        return;
      }
      boolean removed = ApiKeyStore.INSTANCE.revoke(userId, id);
      ex.sendResponseHeaders(removed ? HttpStatus.NO_CONTENT.code() : HttpStatus.NOT_FOUND.code(), -1);
      return;
    }
    ErrorResponses.methodNotAllowed(ex, "GET, POST, DELETE");
  }

  private static Long parseId(String s) {
    try {
      return s == null ? null : Long.valueOf(s.trim());
    } catch (NumberFormatException notNumeric) {
      return null;
    }
  }
}
