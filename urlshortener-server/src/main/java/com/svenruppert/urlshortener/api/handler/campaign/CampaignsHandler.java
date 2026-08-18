package com.svenruppert.urlshortener.api.handler.campaign;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.jsentinel.authorization.api.JSentinelSubject;
import com.svenruppert.jsentinel.authorization.api.permissions.PermissionName;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.urlshortener.api.security.LinkOwnershipStore;
import com.svenruppert.urlshortener.api.security.OwnerGuard;
import com.svenruppert.urlshortener.api.store.campaign.CampaignStatisticsService;
import com.svenruppert.urlshortener.api.store.campaign.CampaignStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestBodyUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.campaign.BatchAssignRequest;
import com.svenruppert.urlshortener.core.campaign.BatchAssignResponse;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignCreateRequest;
import com.svenruppert.urlshortener.core.campaign.CampaignMetaExport;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_CAMPAIGNS;

/**
 * Campaign CRUD + link assignment + rollup statistics under {@code /api/campaigns}.
 * Read/manage is gated by the campaign's owner (or {@code link:read:all});
 * assigning a link additionally requires managing that link ({@link OwnerGuard}).
 */
@RequiresPermission("campaignManage")
public final class CampaignsHandler
    implements HttpHandler, HasLogger {

  private static final PermissionName READ_ALL = new PermissionName("link:read:all");

  private final CampaignStore store;
  private final CampaignStatisticsService stats;

  public CampaignsHandler(CampaignStore store, CampaignStatisticsService stats) {
    this.store = store;
    this.stats = stats;
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    Optional<JSentinelSubject> subject = CurrentSubject.of(ex);
    if (subject.isEmpty()) {
      ErrorResponses.unauthorized(ex);
      return;
    }
    Long userId = OwnerGuard.currentUserId(ex);
    String method = ex.getRequestMethod();
    String path = ex.getRequestURI().getPath();
    String rest = path.length() > PATH_ADMIN_CAMPAIGNS.length()
        ? path.substring(PATH_ADMIN_CAMPAIGNS.length() + 1) : "";
    String[] seg = rest.isEmpty() ? new String[0] : rest.split("/");

    try {
      if (method.equals("POST") && seg.length == 0) {
        create(ex, userId);
        return;
      }
      if (method.equals("GET") && seg.length == 0) {
        list(ex, userId, subject.get());
        return;
      }
      // GET /api/campaigns/tags/{tag}/statistics
      if (method.equals("GET") && seg.length == 3 && seg[0].equals("tags") && seg[2].equals("statistics")) {
        SuccessResponses.okJson(ex, JacksonJson.toJson(stats.forTag(seg[1], from(ex), to(ex))));
        return;
      }
      boolean isAdmin = subject.get().permissionNames().contains(READ_ALL);
      // GET /api/campaigns/export — snapshot of campaign metadata.
      // SEC-H3 (V00.17.00 P040): non-admins get only their own campaigns and
      // the meta of links they own; before, this was a global dump.
      if (method.equals("GET") && seg.length == 1 && seg[0].equals("export")) {
        CampaignMetaExport full = store.exportMeta();
        SuccessResponses.okJson(ex, JacksonJson.toJson(
            isAdmin ? full : scopeToOwner(full, userId)));
        return;
      }
      // POST /api/campaigns/import?replace=true|false — restore a metadata snapshot.
      // SEC-H3 (V00.17.00 P040): the destructive replace mode is admin-only,
      // and a non-admin merge may only touch the caller's own campaigns/links —
      // before, any USER could overwrite (or with replace=true wipe) everything.
      if (method.equals("POST") && seg.length == 1 && seg[0].equals("import")) {
        boolean replace = "true".equalsIgnoreCase(queryParam(ex, "replace"));
        if (replace && !isAdmin) {
          ErrorResponses.forbidden(ex, "link:read:all");
          return;
        }
        CampaignMetaExport data =
            JacksonJson.fromJson(RequestBodyUtils.readBody(ex.getRequestBody()), CampaignMetaExport.class);
        store.importMeta(isAdmin ? data : restrictImportToOwner(data, userId), replace);
        SuccessResponses.noContent(ex);
        return;
      }
      if (seg.length >= 1 && !seg[0].equals("tags")
          && !seg[0].equals("export") && !seg[0].equals("import")) {
        String id = seg[0];
        Optional<Campaign> campaign = store.get(id);
        if (campaign.isEmpty()) {
          ErrorResponses.notFound(ex, "campaign not found");
          return;
        }
        if (!canRead(campaign.get(), userId, subject.get())) {
          ErrorResponses.forbidden(ex, "campaignManage");
          return;
        }
        if (method.equals("GET") && seg.length == 1) {
          SuccessResponses.okJson(ex, JacksonJson.toJson(campaign.get()));
          return;
        }
        if (method.equals("DELETE") && seg.length == 1) {
          store.delete(id);
          SuccessResponses.noContent(ex);
          return;
        }
        if (method.equals("GET") && seg.length == 2 && seg[1].equals("statistics")) {
          SuccessResponses.okJson(ex, JacksonJson.toJson(stats.forCampaign(id, from(ex), to(ex))));
          return;
        }
        // POST /api/campaigns/{id}/links — batch assign (owner-gated per link, skips the rest)
        if (method.equals("POST") && seg.length == 2 && seg[1].equals("links")) {
          batchAssign(ex, id);
          return;
        }
        if (seg.length == 3 && seg[1].equals("links")) {
          String code = seg[2];
          if (method.equals("POST")) {
            if (!OwnerGuard.canManage(ex, code)) {
              ErrorResponses.forbidden(ex, "link:read:all");
              return;
            }
            store.assignLink(code, id);
            SuccessResponses.noContent(ex);
            return;
          }
          if (method.equals("DELETE")) {
            // SEC-L1 (V00.17.00 P030): removing a link from a campaign is a
            // manage operation on that link — owner-gated like assign.
            if (!OwnerGuard.canManage(ex, code)) {
              ErrorResponses.forbidden(ex, "link:read:all");
              return;
            }
            store.removeLink(code);
            SuccessResponses.noContent(ex);
            return;
          }
        }
      }
      ErrorResponses.methodNotAllowed(ex, "GET, POST, DELETE");
    } catch (IllegalArgumentException bad) {
      ErrorResponses.badRequest(ex, bad.getMessage() == null ? "bad request" : bad.getMessage());
    }
  }

  private void batchAssign(HttpExchange ex, String campaignId)
      throws IOException {
    BatchAssignRequest req =
        JacksonJson.fromJson(RequestBodyUtils.readBody(ex.getRequestBody()), BatchAssignRequest.class);
    if (req == null || req.shortCodes() == null || req.shortCodes().isEmpty()) {
      ErrorResponses.badRequest(ex, "shortCodes required");
      return;
    }
    String label = req.label();
    int assigned = 0;
    int skipped = 0;
    for (String code : req.shortCodes()) {
      if (code == null || code.isBlank() || !OwnerGuard.canManage(ex, code)) {
        skipped++;
        continue;
      }
      store.assignLink(code, campaignId);
      if (label != null && !label.isBlank()) store.setLabel(code, label);
      assigned++;
    }
    logger().info("batch assign to {}: assigned={} skipped={}", campaignId, assigned, skipped);
    SuccessResponses.okJson(ex, JacksonJson.toJson(new BatchAssignResponse(assigned, skipped)));
  }

  private void create(HttpExchange ex, Long userId)
      throws IOException {
    CampaignCreateRequest req =
        JacksonJson.fromJson(RequestBodyUtils.readBody(ex.getRequestBody()), CampaignCreateRequest.class);
    if (req == null || req.name() == null || req.name().isBlank()) {
      ErrorResponses.badRequest(ex, "name is required");
      return;
    }
    Campaign c = store.create(req.name().trim(), req.description(), req.type(), req.from(), req.to(), userId);
    logger().info("campaign created id={} owner={}", c.id(), userId);
    SuccessResponses.withStatusJson(ex, HttpStatus.CREATED.code(), JacksonJson.toJson(c));
  }

  private void list(HttpExchange ex, Long userId, JSentinelSubject subject)
      throws IOException {
    boolean all = subject.permissionNames().contains(READ_ALL);
    List<Campaign> result = store.listAll().stream()
        .filter(c -> all || c.ownerUserId() == null || c.ownerUserId().equals(userId))
        .toList();
    SuccessResponses.okJson(ex, JacksonJson.toJson(result));
  }

  private boolean canRead(Campaign c, Long userId, JSentinelSubject subject) {
    if (subject.permissionNames().contains(READ_ALL)) return true;
    return c.ownerUserId() == null || c.ownerUserId().equals(userId);
  }

  private LocalDate from(HttpExchange ex) {
    String v = queryParam(ex, "from");
    return v != null ? LocalDate.parse(v) : LocalDate.now(ZoneOffset.UTC).minusDays(30);
  }

  private LocalDate to(HttpExchange ex) {
    String v = queryParam(ex, "to");
    return v != null ? LocalDate.parse(v) : LocalDate.now(ZoneOffset.UTC);
  }

  private static String queryParam(HttpExchange ex, String key) {
    String q = ex.getRequestURI().getQuery();
    if (q == null) return null;
    for (String p : q.split("&")) {
      int i = p.indexOf('=');
      if (i > 0 && p.substring(0, i).equals(key)) return p.substring(i + 1);
    }
    return null;
  }

  /**
   * SEC-H3: export view for a non-admin — own (or legacy unowned) campaigns,
   * link-level meta only for links the caller owns. The tag registry stays
   * complete: it is ONE global taxonomy by design (concept DECISION 2).
   */
  private static CampaignMetaExport scopeToOwner(CampaignMetaExport full, Long userId) {
    List<Campaign> campaigns = full.campaigns() == null ? List.of()
        : full.campaigns().stream()
            .filter(c -> c.ownerUserId() == null || c.ownerUserId().equals(userId))
            .toList();
    return new CampaignMetaExport(
        full.formatVersion(),
        campaigns,
        filterOwnLinks(full.campaignOfLink(), userId),
        filterOwnLinks(full.linkTags(), userId),
        filterOwnLinks(full.linkLabels(), userId),
        full.knownTags());
  }

  /**
   * SEC-H3: a non-admin merge-import may only touch the caller's own data —
   * campaigns owned by others are dropped, new campaigns become owned by the
   * importer, and link-level entries are kept only for links the caller owns.
   */
  private CampaignMetaExport restrictImportToOwner(CampaignMetaExport data, Long userId) {
    if (data == null) return null;
    List<Campaign> campaigns = new ArrayList<>();
    if (data.campaigns() != null) {
      for (Campaign incoming : data.campaigns()) {
        if (incoming == null || incoming.id() == null) continue;
        Optional<Campaign> existing = store.get(incoming.id());
        if (existing.isPresent()) {
          Long owner = existing.get().ownerUserId();
          if (owner != null && !owner.equals(userId)) continue; // foreign — drop
          campaigns.add(withOwner(incoming, owner));            // keep stored owner
        } else {
          campaigns.add(withOwner(incoming, userId));           // new — importer owns it
        }
      }
    }
    return new CampaignMetaExport(
        data.formatVersion(),
        campaigns,
        filterOwnLinks(data.campaignOfLink(), userId),
        filterOwnLinks(data.linkTags(), userId),
        filterOwnLinks(data.linkLabels(), userId),
        data.knownTags());
  }

  private static Campaign withOwner(Campaign c, Long ownerUserId) {
    return new Campaign(c.id(), c.name(), c.description(), c.type(),
        c.from(), c.to(), ownerUserId, c.createdAt());
  }

  private static <V> Map<String, V> filterOwnLinks(Map<String, V> byShortCode, Long userId) {
    if (byShortCode == null) return null;
    Map<String, V> scoped = new LinkedHashMap<>();
    byShortCode.forEach((code, value) -> {
      if (LinkOwnershipStore.INSTANCE.owner(code).filter(o -> o.equals(userId)).isPresent()) {
        scoped.put(code, value);
      }
    });
    return scoped;
  }
}
