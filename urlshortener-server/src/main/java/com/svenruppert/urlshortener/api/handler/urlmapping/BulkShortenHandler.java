package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.extension.LinkLifecycleHooks;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenRequest;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse.BulkShortenItemResult;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse.ItemStatus;
import com.svenruppert.urlshortener.core.validation.UrlValidator;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.svenruppert.urlshortener.api.security.LinkOwnershipStore;
import com.svenruppert.urlshortener.api.security.OwnerGuard;
import com.svenruppert.urlshortener.core.DefaultValues;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.parseOrBadRequest;

/**
 * POST /api/shorten/bulk
 *
 * <p>Request JSON:
 * <pre>{@code
 * {
 *   "urls": ["https://example.org", "https://other.com", ...],
 *   "defaultExpiresAt": "2025-12-31T23:59:59Z",   // optional
 *   "defaultActive": true                           // optional, defaults to true
 * }
 * }</pre>
 *
 * <p>Responses:
 * <ul>
 *   <li>200 OK – {@link BulkShortenResponse} with per-item results.</li>
 *   <li>400 Bad Request – missing/empty urls list, too many URLs, or unparseable JSON.</li>
 *   <li>405 Method Not Allowed</li>
 * </ul>
 *
 * <p>Each URL is processed independently. Validation failures or store errors produce
 * an error entry in the result without aborting the remaining items.
 *
 * <p>Server-side limits (see {@link BulkShortenRequest}):
 * <ul>
 *   <li>Maximum {@value BulkShortenRequest#MAX_URLS} URLs per request.</li>
 *   <li>Maximum {@value BulkShortenRequest#MAX_URL_LENGTH} characters per URL.</li>
 * </ul>
 */
@RequiresPermission("bulkCreateLinks")
public class BulkShortenHandler implements HttpHandler, HasLogger {

  private final UrlMappingStore store;
  private final LinkLifecycleHooks hooks;

  public BulkShortenHandler(UrlMappingStore store, LinkLifecycleHooks hooks) {
    this.store = store;
    this.hooks = hooks;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    logger().info("BulkShortenHandler - {}", ex.getRequestMethod());
    if (!RequestMethodUtils.requirePost(ex)) return;

    try {
      final String body = readBody(ex.getRequestBody());
      final BulkShortenRequest req = parseOrBadRequest(ex, body, BulkShortenRequest.class);
      if (req == null) return;

      if (req.getUrls() == null || req.getUrls().isEmpty()) {
        ErrorResponses.badRequest(ex, "Missing or empty 'urls' list");
        return;
      }
      if (req.getUrls().size() > BulkShortenRequest.MAX_URLS) {
        ErrorResponses.badRequest(ex,
            "Too many URLs: " + req.getUrls().size()
                + " exceeds limit of " + BulkShortenRequest.MAX_URLS);
        return;
      }

      final Instant expiresAt    = req.getDefaultExpiresAt();
      final boolean active       = req.effectiveActive();
      final List<BulkShortenItemResult> results = new ArrayList<>(req.getUrls().size());

      for (int i = 0; i < req.getUrls().size(); i++) {
        final String raw = req.getUrls().get(i);
        final String url = raw == null ? "" : raw.strip();

        // ── blank ───────────────────────────────────────────────────────────
        if (url.isBlank()) {
          results.add(BulkShortenItemResult.invalidUrl(i,
              raw != null ? raw : "", "URL must not be blank"));
          continue;
        }

        // ── length guard ────────────────────────────────────────────────────
        if (url.length() > BulkShortenRequest.MAX_URL_LENGTH) {
          results.add(BulkShortenItemResult.tooLong(i, url));
          continue;
        }

        // ── format validation ───────────────────────────────────────────────
        final var validation = UrlValidator.validate(url);
        if (!validation.valid()) {
          results.add(BulkShortenItemResult.invalidUrl(i, url, validation.message()));
          continue;
        }

        // ── store ────────────────────────────────────────────────────────────
        final var mappingResult = store.createMapping(null, url, expiresAt, active);

        if (mappingResult.isSuccess()) {
          final var mapping   = mappingResult.getOrThrow();
          final String shortUrl = resolveShortUrl(mapping.shortCode());
          LinkOwnershipStore.INSTANCE.record(
              mapping.shortCode(),
              OwnerGuard.currentUserId(ex));
          // plug-in hook (V00.18.00 P085): batch-wide metadata rides along in
          // the raw JSON; without a plug-in this is a no-op
          hooks.linkCreated(mapping.shortCode(), body, true);
          results.add(BulkShortenItemResult.success(i, url, mapping.shortCode(), shortUrl));
        } else {
          final AtomicReference<String> errorMsg = new AtomicReference<>("Creation failed");
          mappingResult.peekFailure(errorJson -> {
            try {
              var parsed  = JsonUtils.parseJson(errorJson);
              var message = parsed.get("message");
              if (message != null && !message.isBlank()) errorMsg.set(message);
            } catch (IOException ignored) { }
          });
          results.add(BulkShortenItemResult.failed(i, url, errorMsg.get()));
        }
      }

      logger().info("BulkShortenHandler processed {} URLs – {} succeeded, {} failed",
                    results.size(),
                    results.stream().filter(r -> r.getStatus() == ItemStatus.CREATED).count(),
                    results.stream().filter(r -> r.getStatus() != ItemStatus.CREATED).count());

      SuccessResponses.ok(ex, new BulkShortenResponse(results));

    } catch (IllegalArgumentException e) {
      logger().warn("bad request – {}", e.getMessage());
      ErrorResponses.badRequest(ex, e.getMessage());
    } catch (Exception e) {
      logger().error("internal error", e);
      ErrorResponses.internalServerError(ex, "internal_error");
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  /**
   * Builds the full short URL from the shortcode.
   * Uses the configured base URL from {@link com.svenruppert.urlshortener.core.DefaultValues}.
   */
  private static String resolveShortUrl(String shortCode) {
    return DefaultValues.SHORTCODE_BASE_URL + shortCode;
  }
}
