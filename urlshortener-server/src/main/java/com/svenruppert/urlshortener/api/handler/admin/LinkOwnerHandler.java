package com.svenruppert.urlshortener.api.handler.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.audit.JCustosAuditService;
import eu.jsentinel.jcustos.audit.PolicyEvaluated;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import com.svenruppert.urlshortener.api.security.LinkOwnershipStore;
import com.svenruppert.urlshortener.api.security.OwnerGuard;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.jsonMapOrBadRequest;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_LINK_OWNER;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * PUT {@code /api/admin/links/owner/{shortCode}} {"userId": n} — admin-only
 * owner (re)assignment (V00.17.00 P020, concept DECISION 1). Lets the first
 * admin redistribute the backfilled links and resolves ownership disputes.
 * Every transfer is written to the security audit log.
 */
@RequiresPermission("linkOwnerManage")
public class LinkOwnerHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingStore store;

  public LinkOwnerHandler(UrlMappingStore store) {
    this.store = store;
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePut(ex)) return;
    try {
      String path = ex.getRequestURI().getPath();
      String shortCode = path.length() > PATH_ADMIN_LINK_OWNER.length() + 1
          ? URLDecoder.decode(path.substring(PATH_ADMIN_LINK_OWNER.length() + 1), StandardCharsets.UTF_8)
          : "";
      if (isNullOrBlank(shortCode)) {
        ErrorResponses.badRequest(ex, "shortCode path segment is required");
        return;
      }
      if (!store.existsByCode(shortCode)) {
        ErrorResponses.notFound(ex, "unknown short code");
        return;
      }

      Map<String, String> body = jsonMapOrBadRequest(ex);

      if (body == null) return;
      String rawUserId = body == null ? null : body.get("userId");
      Long newOwnerId = parseUserId(rawUserId);
      if (newOwnerId == null) {
        ErrorResponses.badRequest(ex, "numeric 'userId' is required");
        return;
      }
      Optional<ApiUser> newOwner = UserDirectoryProvider.directory().findById(newOwnerId);
      if (newOwner.isEmpty()) {
        ErrorResponses.notFound(ex, "unknown user id");
        return;
      }

      Long previousOwner = LinkOwnershipStore.INSTANCE.owner(shortCode).orElse(null);
      LinkOwnershipStore.INSTANCE.record(shortCode, newOwnerId);
      // AuditEvent is sealed — PolicyEvaluated is the permitted type that
      // carries subject + action + free-text detail for the transfer trail.
      audit(new PolicyEvaluated(
          Instant.now(Clock.systemUTC()),
          String.valueOf(OwnerGuard.currentUserId(ex)),
          "link-owner-transfer",
          "transferred",
          "shortCode=" + shortCode + " from=" + previousOwner + " to=" + newOwnerId));
      logger().info("link '{}' owner transferred {} -> {}", shortCode, previousOwner, newOwnerId);

      SuccessResponses.okJson(ex, JsonUtils.toJson(Map.of(
          "shortCode", shortCode,
          "ownerUserId", String.valueOf(newOwnerId))));
    } catch (RuntimeException e) {
      logger().error("owner transfer failed", e);
      ErrorResponses.internalServerError(ex, "internal_error");
    }
  }

  private static Long parseUserId(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Long.valueOf(raw.trim());
    } catch (NumberFormatException notNumeric) {
      return null;
    }
  }

  private static void audit(PolicyEvaluated event) {
    try {
      JCustosAuditService sink = JCustosServiceResolver.securityAuditService();
      sink.publish(event);
    } catch (RuntimeException ignored) {
      // audit must never block the transfer itself
    }
  }
}
