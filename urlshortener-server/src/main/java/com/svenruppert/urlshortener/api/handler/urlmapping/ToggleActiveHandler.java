package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.urlmapping.ToggleActive.ToggleActiveRequest;
import com.svenruppert.urlshortener.core.urlmapping.ToggleActive.ToggleActiveResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.api.security.OwnerGuard;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.parseOrBadRequest;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * Toggles the "active" state of a ShortUrlMapping.
 * <p>
 * Request JSON:
 * {
 * "shortURL": "abc123",
 * "active": true
 * }
 * <p>
 * Response:
 * 200 OK + updated mapping as JSON
 * 400 Bad Request (missing fields, invalid alias)
 * 404 Not Found (mapping not found)
 */
@RequiresPermission("toggleActive")
public class ToggleActiveHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingStore store;

  public ToggleActiveHandler(UrlMappingStore store) {
    this.store = store;
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    logger().info("handle ... {} ", ex.getRequestMethod());
    if (!RequestMethodUtils.requirePut(ex)) return;

    try {
      final String body = readBody(ex.getRequestBody());
      ToggleActiveRequest req = parseOrBadRequest(ex, body, ToggleActiveRequest.class);
      if (req == null) return;
      var shortCode = req.shortCode();
      if (isNullOrBlank(shortCode)) {
        ErrorResponses.badRequest(ex, "Missing 'shortCode'");
        return;
      }
      // SEC-H2 (V00.17.00 P030): only the owner or link:read:all may toggle —
      // without this check any USER could deactivate foreign links.
      if (!OwnerGuard.canManage(ex, shortCode)) {
        logger().info("toggle denied — not owner of '{}'", shortCode);
        ErrorResponses.withStatus(ex, HttpStatus.FORBIDDEN,
            "not the owner of this alias");
        return;
      }
      boolean newActiveValue = req.active();
      logger().info("Toggling active status for {} to {}", shortCode, newActiveValue);
      Result<ToggleActiveResponse, String> mapping = store.toggleActive(shortCode, newActiveValue);

      if (mapping.isSuccess()) {
        logger().info("Toggling active status successfully");
        SuccessResponses.ok(ex, mapping.getOrThrow());
      } else {
        AtomicReference<String> reason = new AtomicReference<>("toggling active status failed");
        mapping.peekFailure(failed -> {
          reason.set(failed);
          logger().info("Toggling active status failed: {}", failed);
        });
        // The store reports a missing alias as an ordinary failure string, and
        // this used to become a 400 — a malformed-request answer to a perfectly
        // well-formed request. The Javadoc above has promised 404 all along, and
        // DeleteMappingHandler and EditMappingHandler answer the same case that
        // way. Ask the store which of the two it is instead of guessing from the
        // failure text.
        if (!store.existsByCode(shortCode)) {
          ErrorResponses.notFound(ex, "short code not found");
        } else {
          ErrorResponses.badRequest(ex, reason.get());
        }
      }
    } catch (RuntimeException e) {
      logger().warn("catch - {}", e.toString());
      ErrorResponses.internalServerError(ex, e);
    } finally {
      logger().info("ToggleActiveHandler .. finally");
      ex.close();
    }
  }
}
