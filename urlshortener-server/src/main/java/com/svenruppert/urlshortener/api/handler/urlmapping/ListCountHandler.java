package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.campaign.CampaignStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingLookup;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.svenruppert.urlshortener.api.utils.QueryUtils.*;

@RequiresPermission("listLinksCount")
public final class ListCountHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingLookup store;
  private final CampaignStore campaignStore;

  public ListCountHandler(UrlMappingLookup store, CampaignStore campaignStore) {
    this.store = store;
    this.campaignStore = campaignStore;
  }

  private Set<String> tagRestriction(Map<String, List<String>> query) {
    String tag = first(query, "tag");
    if (tag == null || tag.isBlank()) return null;
    return campaignStore.linksOfTag(tag);
  }

  /** SEC-M2 (V00.17.00 P050): tag filter intersected with the caller's read-scope. */
  private Set<String> combinedRestriction(Map<String, List<String>> query, HttpExchange ex) {
    Set<String> tags = tagRestriction(query);
    Set<String> owned = com.svenruppert.urlshortener.api.security.OwnerGuard.readRestriction(ex);
    if (owned == null) return tags;
    if (tags == null) return owned;
    Set<String> both = new java.util.LinkedHashSet<>(tags);
    both.retainAll(owned);
    return both;
  }


  private static boolean bool(Map<String, List<String>> m, String k) {
    String v = first(m, k);
    return v != null && (v.equalsIgnoreCase("true") || v.equals("1"));
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requireGet(ex)) return;
    var rawQuery = ex.getRequestURI().getRawQuery();
    var queryString = Optional.ofNullable(rawQuery).orElse("");
    Map<String, List<String>> q = parseQueryParams(queryString);

    // sort/page/size not relevant
    UrlMappingFilter filter = UrlMappingFilter.builder()
        .codePart(first(q, "code"))
        .urlPart(first(q, "url"))
        .createdFrom(parseInstant(first(q, "from"), true).orElse(null))
        .createdTo(parseInstant(first(q, "to"), false).orElse(null))
        .active(parseBoolean(first(q, "active")).orElse(null))
        .restrictToCodes(combinedRestriction(q, ex))
        .build();

    int total = store.count(filter);
    var data = "{\"total\":" + total + "}";

    SuccessResponses.okJson(ex, data);


  }
}
