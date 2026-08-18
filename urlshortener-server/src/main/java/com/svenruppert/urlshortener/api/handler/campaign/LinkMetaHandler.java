package com.svenruppert.urlshortener.api.handler.campaign;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.urlshortener.api.security.OwnerGuard;
import com.svenruppert.urlshortener.api.store.campaign.CampaignStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestBodyUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.campaign.SetLabelRequest;
import com.svenruppert.urlshortener.core.campaign.SetTagsRequest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_LINK_META;

/**
 * Per-link metadata under {@code /api/link-meta}: read the campaign/tags/label of
 * a link and set its tags or placement label. Mutations require managing the link
 * ({@link OwnerGuard}: owner or {@code link:read:all}).
 */
@RequiresPermission("campaignManage")
public final class LinkMetaHandler
    implements HttpHandler, HasLogger {

  private final CampaignStore store;

  public LinkMetaHandler(CampaignStore store) {
    this.store = store;
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (CurrentSubject.of(ex).isEmpty()) {
      ErrorResponses.unauthorized(ex);
      return;
    }
    String method = ex.getRequestMethod();
    String path = ex.getRequestURI().getPath();
    String rest = path.length() > PATH_ADMIN_LINK_META.length()
        ? path.substring(PATH_ADMIN_LINK_META.length() + 1) : "";
    String[] seg = rest.isEmpty() ? new String[0] : rest.split("/");

    try {
      if (seg.length == 1 && method.equals("GET")) {
        String code = seg[0];
        // SEC-L1 (V00.17.00 P030): meta is owner-scoped like the mutations.
        if (!OwnerGuard.canManage(ex, code)) {
          ErrorResponses.forbidden(ex, "link:read:all");
          return;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shortCode", code);
        out.put("campaignId", store.campaignIdOfLink(code).orElse(null));
        out.put("tags", store.tagsOfLink(code));
        out.put("label", store.labelOfLink(code).orElse(null));
        SuccessResponses.okJson(ex, JacksonJson.toJson(out));
        return;
      }
      if (seg.length == 2 && method.equals("PUT")) {
        String code = seg[0];
        if (!OwnerGuard.canManage(ex, code)) {
          ErrorResponses.forbidden(ex, "link:read:all");
          return;
        }
        String body = RequestBodyUtils.readBody(ex.getRequestBody());
        if (seg[1].equals("tags")) {
          SetTagsRequest req = JacksonJson.fromJson(body, SetTagsRequest.class);
          Set<String> tags = (req == null || req.tags() == null) ? Set.of() : new LinkedHashSet<>(req.tags());
          store.setTags(code, tags);
          SuccessResponses.noContent(ex);
          return;
        }
        if (seg[1].equals("label")) {
          SetLabelRequest req = JacksonJson.fromJson(body, SetLabelRequest.class);
          store.setLabel(code, req == null ? null : req.label());
          SuccessResponses.noContent(ex);
          return;
        }
      }
      ErrorResponses.methodNotAllowed(ex, "GET, PUT");
    } catch (IllegalArgumentException bad) {
      ErrorResponses.badRequest(ex, bad.getMessage() == null ? "bad request" : bad.getMessage());
    }
  }
}
