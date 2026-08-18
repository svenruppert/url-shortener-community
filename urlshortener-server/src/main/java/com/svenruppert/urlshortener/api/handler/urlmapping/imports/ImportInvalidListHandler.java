package com.svenruppert.urlshortener.api.handler.urlmapping.imports;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.imports.ImportStaging;
import com.svenruppert.urlshortener.api.store.imports.ImportStagingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.ImportListQueryUtils;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;

import java.io.IOException;
import java.util.*;

import static com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportStagingHandlerSupport.*;

@RequiresPermission("importInvalid")
public final class ImportInvalidListHandler
    implements HttpHandler, HasLogger {

  private final ImportStagingStore stagingStore;

  public ImportInvalidListHandler(ImportStagingStore stagingStore) {
    this.stagingStore = stagingStore;
  }

  private static Map<String, String> toFlatInvalid(ImportStaging.InvalidItem it) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("shortCode", it.shortCode());
    m.put("reason", it.reason());
    return m;
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

      List<ImportStaging.InvalidItem> all = opt.get().invalidItems();
      int total = all == null ? 0 : all.size();

      ImportListQueryUtils.Slice slice = sliceFromPaging(paging, total);

      List<Map<String, String>> items = new ArrayList<>();
      if (total > 0 && slice.startInclusive() < slice.endExclusive()) {
        for (ImportStaging.InvalidItem it
            : all.subList(slice.startInclusive(), slice.endExclusive())) {
          items.add(toFlatInvalid(it));
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
      logger().warn("Import invalid list failed", e);
      ErrorResponses.badRequest(ex, String.valueOf(e.getMessage()));
    }
  }
}
