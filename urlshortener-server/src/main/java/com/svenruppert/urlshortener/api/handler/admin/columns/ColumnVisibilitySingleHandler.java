package com.svenruppert.urlshortener.api.handler.admin.columns;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.core.prefs.ColumnDeleteRequest;
import com.svenruppert.urlshortener.core.prefs.ColumnSingleEditRequest;

import java.io.IOException;
import java.util.Map;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.parseOrBadRequest;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.jsonBodyOrBadRequest;
import static com.svenruppert.urlshortener.api.utils.RequestMethodUtils.allow;
import static com.svenruppert.urlshortener.api.utils.RequestMethodUtils.methodNotAllowed;
import static com.svenruppert.urlshortener.core.StringUtils.isBlank;

@RequiresPermission("preferencesColumnsSingle")
public class ColumnVisibilitySingleHandler
    implements HttpHandler, HasLogger {

  private final PreferencesStore store;

  public ColumnVisibilitySingleHandler(PreferencesStore store) {
    this.store = store;
  }


  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    switch (ex.getRequestMethod()) {
      case "PUT" -> handleSingleEdit(ex);
      case "DELETE" -> handleSingleDelete(ex);
      // DELETE is served two lines up, so it belongs in Allow — a client doing
      // method discovery via OPTIONS was never told it exists. The sibling
      // ColumnVisibilityHandler lists its methods correctly.
      case "OPTIONS" -> allow(ex, "PUT, DELETE, OPTIONS");
      default -> methodNotAllowed(ex, "PUT, DELETE, OPTIONS");
    }
  }

  private void handleSingleDelete(HttpExchange ex)
      throws IOException {
    final var body = readBody(ex.getRequestBody());
    final ColumnDeleteRequest req;
    try {
      req = parseOrBadRequest(ex, body, ColumnDeleteRequest.class);
      if (req == null) return;
    } catch (Exception e) {
      writeJson(ex, BAD_REQUEST, Map.of("error", "Invalid JSON: " + e.getMessage()));
      return;
    }
    if (isBlank(req.userId()) || isBlank(req.viewId()) || isBlank(req.columnKey())) {
      writeJson(ex, BAD_REQUEST, Map.of("error", "userId, viewId and columnKey required"));
      return;
    }

    store.delete(req.userId(), req.viewId(), req.columnKey());
    ex.sendResponseHeaders(NO_CONTENT.code(), -1);
  }

  private void handleSingleEdit(HttpExchange ex)
      throws IOException {
    logger().info("handleSingleEdit..");
    var req = jsonBodyOrBadRequest(ex, ColumnSingleEditRequest.class);
    if (req == null) return;
    if (isBlank(req.userId()) || isBlank(req.viewId()) || isBlank(req.columnKey())) {
      writeJson(ex, BAD_REQUEST, Map.of("error", "userId, viewId and columnKey required"));
      return;
    }
    var visibility = Map.of(req.columnKey(), req.visible());
    logger().info("handleSingleEdit - visibility {}", visibility);
    store.saveColumnVisibilities(req.userId(), req.viewId(), visibility);
    writeJson(ex, OK, Map.of("status", "ok"));
  }


}
