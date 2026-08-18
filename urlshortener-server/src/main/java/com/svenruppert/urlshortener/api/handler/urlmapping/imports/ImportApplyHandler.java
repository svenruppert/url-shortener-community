package com.svenruppert.urlshortener.api.handler.urlmapping.imports;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.imports.ImportStaging;
import com.svenruppert.urlshortener.api.store.imports.ImportStagingStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RequiresPermission("importApply")
public final class ImportApplyHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingStore store;
  private final ImportStagingStore stagingStore;

  public ImportApplyHandler(UrlMappingStore store, ImportStagingStore stagingStore) {
    this.store = store;
    this.stagingStore = stagingStore;
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    Map<String, String> m = new HashMap<>();
    if (rawQuery == null || rawQuery.isBlank()) return m;

    for (String p : rawQuery.split("&")) {
      int idx = p.indexOf('=');
      if (idx <= 0) continue;
      String k = URLDecoder.decode(p.substring(0, idx), StandardCharsets.UTF_8);
      String v = URLDecoder.decode(p.substring(idx + 1), StandardCharsets.UTF_8);
      m.put(k, v);
    }
    return m;
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePost(ex)) return;

    Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
    String id = q.get("stagingId");
    if (id == null || id.isBlank()) {
      ErrorResponses.missingParameter(ex, "stagingId");
      return;
    }

    var opt = stagingStore.get(id);
    if (opt.isEmpty()) {
      ErrorResponses.stagingNotFound(ex);
      return;
    }

    ImportStaging staging = opt.get();

    int created = 0;
    int skippedConflicts = staging.conflicts().size();

    Long importingUser = com.svenruppert.urlshortener.api.security.OwnerGuard.currentUserId(ex);
    for (var m : staging.newItems()) {
      Instant expires = m.expiresAt().orElse(null);
      var res = store.createMapping(m.createdAt(), m.shortCode(), m.originalUrl(), expires, m.active());
      if (res.isSuccess()) {
        created++;
        com.svenruppert.urlshortener.api.security.LinkOwnershipStore.INSTANCE.record(
            m.shortCode(), importingUser);
      }
    }

    stagingStore.remove(id);

    String body = "{"
        + "\"created\":" + created + ","
        + "\"skippedConflicts\":" + skippedConflicts + ","
        + "\"invalid\":" + staging.invalidItems().size()
        + "}";

    SuccessResponses.okJson(ex, body);

  }
}
