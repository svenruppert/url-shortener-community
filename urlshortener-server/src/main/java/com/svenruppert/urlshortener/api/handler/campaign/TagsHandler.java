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
import com.svenruppert.urlshortener.core.campaign.AssignTagRequest;
import com.svenruppert.urlshortener.core.campaign.CreateTagRequest;
import com.svenruppert.urlshortener.core.campaign.DeleteTagRequest;
import com.svenruppert.urlshortener.core.campaign.RenameTagRequest;
import com.svenruppert.urlshortener.core.campaign.ReplaceTagRequest;
import com.svenruppert.urlshortener.core.campaign.TagInfo;

import java.io.IOException;
import java.util.List;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_TAGS;

/**
 * Central tag management under {@code /api/tags}: list all distinct tags with
 * their link counts, and rename (merge) or delete a tag across every link.
 *
 * <p>Tags are a global, cross-cutting namespace (not owner-scoped), so these
 * operations affect every link carrying the tag; they require the
 * {@code campaignManage} permission like the other campaign handlers.
 */
@RequiresPermission("campaignManage")
public final class TagsHandler
    implements HttpHandler, HasLogger {

  private final CampaignStore store;

  public TagsHandler(CampaignStore store) {
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
    String rest = path.length() > PATH_ADMIN_TAGS.length()
        ? path.substring(PATH_ADMIN_TAGS.length() + 1) : "";
    String[] seg = rest.isEmpty() ? new String[0] : rest.split("/");

    try {
      if (seg.length == 0 && method.equals("GET")) {
        List<TagInfo> tags = store.tagCounts().entrySet().stream()
            .map(e -> new TagInfo(e.getKey(), e.getValue()))
            .toList();
        SuccessResponses.okJson(ex, JacksonJson.toJson(tags));
        return;
      }
      // SEC-L1 / concept DECISION 2 (V00.17.00 P060): the registry is ONE
      // global taxonomy — mutating it (create/rename/replace/delete) is
      // admin-only. Attaching tags to own links stays a USER operation.
      boolean registryMutation = seg.length == 1 && method.equals("POST")
          && !seg[0].equals("assign");
      if (registryMutation && !OwnerGuard.canReadAll(ex)) {
        ErrorResponses.forbidden(ex, "link:read:all");
        return;
      }
      if (seg.length == 1 && method.equals("POST") && seg[0].equals("create")) {
        String body = RequestBodyUtils.readBody(ex.getRequestBody());
        CreateTagRequest req = JacksonJson.fromJson(body, CreateTagRequest.class);
        if (req == null || req.name() == null || req.name().isBlank()) {
          ErrorResponses.badRequest(ex, "Missing 'name'");
          return;
        }
        store.createTag(req.name());
        SuccessResponses.noContent(ex);
        return;
      }
      if (seg.length == 1 && method.equals("POST") && seg[0].equals("rename")) {
        String body = RequestBodyUtils.readBody(ex.getRequestBody());
        RenameTagRequest req = JacksonJson.fromJson(body, RenameTagRequest.class);
        if (req == null || req.from() == null || req.from().isBlank()) {
          ErrorResponses.badRequest(ex, "Missing 'from'");
          return;
        }
        store.renameTag(req.from(), req.to());
        SuccessResponses.noContent(ex);
        return;
      }
      if (seg.length == 1 && method.equals("POST") && seg[0].equals("assign")) {
        String body = RequestBodyUtils.readBody(ex.getRequestBody());
        AssignTagRequest req = JacksonJson.fromJson(body, AssignTagRequest.class);
        if (req == null || req.tag() == null || req.tag().isBlank()
            || req.shortCodes() == null || req.shortCodes().isEmpty()) {
          ErrorResponses.badRequest(ex, "Missing 'tag' or 'shortCodes'");
          return;
        }
        // SEC-L1 (V00.17.00 P060): attaching is per-link owner-gated — foreign
        // codes are skipped, mirroring the campaign batch-assign semantics.
        List<String> ownCodes = req.shortCodes().stream()
            .filter(code -> code != null && !code.isBlank() && OwnerGuard.canManage(ex, code))
            .toList();
        if (!ownCodes.isEmpty()) {
          store.addTagToLinks(req.tag(), ownCodes);
        }
        SuccessResponses.noContent(ex);
        return;
      }
      if (seg.length == 1 && method.equals("POST") && seg[0].equals("replace")) {
        String body = RequestBodyUtils.readBody(ex.getRequestBody());
        ReplaceTagRequest req = JacksonJson.fromJson(body, ReplaceTagRequest.class);
        if (req == null || req.from() == null || req.from().isBlank()) {
          ErrorResponses.badRequest(ex, "Missing 'from'");
          return;
        }
        store.replaceTag(req.from(), req.to());
        SuccessResponses.noContent(ex);
        return;
      }
      if (seg.length == 1 && method.equals("POST") && seg[0].equals("delete")) {
        String body = RequestBodyUtils.readBody(ex.getRequestBody());
        DeleteTagRequest req = JacksonJson.fromJson(body, DeleteTagRequest.class);
        if (req == null || req.tag() == null || req.tag().isBlank()) {
          ErrorResponses.badRequest(ex, "Missing 'tag'");
          return;
        }
        store.deleteTag(req.tag());
        SuccessResponses.noContent(ex);
        return;
      }
      ErrorResponses.methodNotAllowed(ex, "GET, POST");
    } catch (IllegalArgumentException bad) {
      ErrorResponses.badRequest(ex, bad.getMessage() == null ? "bad request" : bad.getMessage());
    }
  }
}
