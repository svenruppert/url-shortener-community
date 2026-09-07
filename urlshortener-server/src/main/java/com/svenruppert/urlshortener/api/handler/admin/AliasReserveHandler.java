package com.svenruppert.urlshortener.api.handler.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.security.ReservedAliasStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.core.JacksonJson;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.jsonMapOrBadRequest;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_ALIASES;

/**
 * Reserve aliases for future use (P300, commercial). Admin-only ({@code storeInfo}).
 * A reserved alias is added to {@link AliasPolicy} so the create path rejects it
 * until it is released again.
 * <ul>
 *   <li>{@code POST /api/admin/aliases/reserve} {"alias"}</li>
 *   <li>{@code GET /api/admin/aliases/reserved}</li>
 *   <li>{@code DELETE /api/admin/aliases/reserved/{alias}}</li>
 * </ul>
 */
@RequiresPermission("storeInfo")
public final class AliasReserveHandler
    implements HttpHandler, HasLogger {

  private static final String RESERVE = PATH_ADMIN_ALIASES + "/reserve";
  private static final String RESERVED = PATH_ADMIN_ALIASES + "/reserved";

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    String method = ex.getRequestMethod();
    String path = ex.getRequestURI().getPath();

    if ("POST".equalsIgnoreCase(method) && RESERVE.equals(path)) {
      Map<String, String> body = jsonMapOrBadRequest(ex);
      if (body == null) return;
      String alias = body == null ? null : body.get("alias");
      AliasPolicy.Validation v = AliasPolicy.validate(alias);
      if (v.failed()) {
        if (v.reason() == AliasPolicy.Reason.RESERVED) {
          ErrorResponses.conflict(ex, "alias already reserved or in use");
        } else {
          ErrorResponses.badRequest(ex, v.reason().defaultMessage);
        }
        return;
      }
      AliasPolicy.reserve(List.of(alias));
      ReservedAliasStore.INSTANCE.add(alias);
      logger().info("alias reserved: {}", alias);
      SuccessResponses.withStatusJson(ex, HttpStatus.CREATED.code(),
          JacksonJson.toJson(Map.of("alias", AliasPolicy.normalize(alias), "reserved", true)));
      return;
    }
    if ("GET".equalsIgnoreCase(method) && RESERVED.equals(path)) {
      List<Map<String, Object>> list = ReservedAliasStore.INSTANCE.all().entrySet().stream()
          .map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("alias", e.getKey());
            m.put("reservedAt", e.getValue().toString());
            return m;
          })
          .toList();
      SuccessResponses.okJson(ex, JacksonJson.toJson(list));
      return;
    }
    if ("DELETE".equalsIgnoreCase(method) && path.startsWith(RESERVED + "/")) {
      String alias = path.substring((RESERVED + "/").length());
      if (alias.isBlank()) {
        ErrorResponses.badRequest(ex, "expected DELETE " + RESERVED + "/{alias}");
        return;
      }
      boolean removed = ReservedAliasStore.INSTANCE.remove(alias);
      if (removed) {
        AliasPolicy.unreserve(List.of(alias));
      }
      ex.sendResponseHeaders(removed ? HttpStatus.NO_CONTENT.code() : HttpStatus.NOT_FOUND.code(), -1);
      return;
    }
    ErrorResponses.methodNotAllowed(ex, "GET, POST, DELETE");
  }
}
