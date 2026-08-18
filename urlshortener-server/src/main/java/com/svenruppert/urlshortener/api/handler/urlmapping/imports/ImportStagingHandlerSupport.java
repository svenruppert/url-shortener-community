package com.svenruppert.urlshortener.api.handler.urlmapping.imports;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.urlshortener.api.store.imports.ImportStaging;
import com.svenruppert.urlshortener.api.store.imports.ImportStagingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.ImportListQueryUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.DefaultValues.*;

final class ImportStagingHandlerSupport {

  private ImportStagingHandlerSupport() {
  }

  static Optional<ImportStaging> resolveStagingOrRespond(HttpExchange ex,
                                                         ImportStagingStore store,
                                                         Map<String, String> q)
      throws IOException {

    String stagingId = q.get("stagingId");
    if (stagingId == null || stagingId.isBlank()) {
      ErrorResponses.missingParameter(ex, "stagingId");
      return Optional.empty();
    }

    Optional<ImportStaging> opt = store.get(stagingId);
    if (opt.isEmpty()) {
      ErrorResponses.stagingNotFound(ex);
      return Optional.empty();
    }
    return opt;
  }

  static Paging pagingFromQuery1Based(Map<String, String> q) {
    // 1-basierte Semantik beibehalten
    int page = ImportListQueryUtils.clampInt(
        ImportListQueryUtils.parseInt(q.get("page"), DEFAULT_PAGE),
        1,
        Integer.MAX_VALUE
    );

    int size = ImportListQueryUtils.clampInt(
        ImportListQueryUtils.parseInt(q.get("size"), DEFAULT_SIZE),
        1,
        MAX_SIZE
    );

    return new Paging(page, size);
  }

  static ImportListQueryUtils.Slice sliceFromPaging(Paging paging, int total) {
    // Utility slice ist 0-basiert → page - 1
    return ImportListQueryUtils.slice(paging.page() - 1, paging.size(), total);
  }

  record Paging(int page, int size) {
  }
}
