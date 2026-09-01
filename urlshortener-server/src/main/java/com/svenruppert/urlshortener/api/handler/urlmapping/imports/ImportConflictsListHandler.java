package com.svenruppert.urlshortener.api.handler.urlmapping.imports;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.imports.ImportStaging;
import com.svenruppert.urlshortener.api.store.imports.ImportStagingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.ImportListQueryUtils;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportStagingHandlerSupport.*;

@RequiresPermission("importConflicts")
public final class ImportConflictsListHandler
    implements HttpHandler, HasLogger {

  private final ImportStagingStore stagingStore;

  public ImportConflictsListHandler(ImportStagingStore stagingStore) {
    this.stagingStore = stagingStore;
  }

  private static Map<String, String> toFlatConflict(ImportStaging.Conflict c) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("shortCode", c.shortCode());

    ShortUrlMapping existing = c.existing();
    ShortUrlMapping incoming = c.incoming();

    m.put("existingUrl", existing != null ? existing.originalUrl() : null);
    m.put("incomingUrl", incoming != null ? incoming.originalUrl() : null);

    m.put("existingActive", existing != null ? String.valueOf(existing.active()) : null);
    m.put("incomingActive", incoming != null ? String.valueOf(incoming.active()) : null);

    Instant exExp = existing != null ? existing.expiresAt().orElse(null) : null;
    Instant inExp = incoming != null ? incoming.expiresAt().orElse(null) : null;
    m.put("existingExpiresAt", exExp != null ? exExp.toString() : null);
    m.put("incomingExpiresAt", inExp != null ? inExp.toString() : null);

    m.put("diff", diff(existing, incoming));
    return m;
  }

  private static String diff(ShortUrlMapping a, ShortUrlMapping b) {
    if (a == null || b == null) return "UNKNOWN";
    List<String> diffs = new ArrayList<>();
    if (!Objects.equals(a.originalUrl(), b.originalUrl())) diffs.add("URL");
    if (!Objects.equals(a.createdAt(), b.createdAt())) diffs.add("CREATED_AT");
    if (!Objects.equals(a.expiresAt().orElse(null), b.expiresAt().orElse(null))) diffs.add("EXPIRES_AT");
    if (a.active() != b.active()) diffs.add("ACTIVE");
    return diffs.isEmpty() ? "NONE" : String.join("|", diffs);
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {

    if (!RequestMethodUtils.requireGet(ex)) return;

    try {
      Map<String, String> q =
          ImportListQueryUtils.parseQueryParamsSingle(ex.getRequestURI().getRawQuery());

      Optional<ImportStaging> opt = resolveStagingOrRespond(ex, stagingStore, q);
      if (opt.isEmpty()) return;

      Paging paging = pagingFromQuery1Based(q);

      List<ImportStaging.Conflict> all = opt.get().conflicts();
      int total = all == null ? 0 : all.size();

      ImportListQueryUtils.Slice slice = sliceFromPaging(paging, total);

      List<Map<String, String>> items = new ArrayList<>();
      if (total > 0 && slice.startInclusive() < slice.endExclusive()) {
        for (ImportStaging.Conflict c : all.subList(slice.startInclusive(), slice.endExclusive())) {
          items.add(toFlatConflict(c));
        }
      }

      String json = JsonUtils.toJsonListingPaged(
          "import-staging",
          items.size(),
          items,
          paging.page(),
          paging.size(),
          total,
          null,
          null
      );

      SuccessResponses.ok(ex, json);


    } catch (Exception e) {
      logger().warn("Import conflicts list failed", e);
      ErrorResponses.badRequest(ex, "exception", String.valueOf(e.getMessage()));
    }
  }
}
