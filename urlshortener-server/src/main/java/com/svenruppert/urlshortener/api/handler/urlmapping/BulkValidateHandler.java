package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.urlmapping.BulkValidateRequest;
import com.svenruppert.urlshortener.core.urlmapping.BulkValidateResponse;
import com.svenruppert.urlshortener.core.urlmapping.BulkValidateResponse.ExistingShortlinkInfo;
import com.svenruppert.urlshortener.core.urlmapping.BulkValidateResponse.ValidationItemResult;
import com.svenruppert.urlshortener.core.validation.UrlValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.JsonUtils.fromJson;

/**
 * POST /api/validate/bulk
 *
 * <p>Validates a batch of URLs without persisting any data.
 * Returns a per-entry {@link BulkValidateResponse.ValidationItemResult} with a machine-readable
 * {@link BulkValidateResponse.ValidationStatus}.
 *
 * <p>Checks performed in order:
 * <ol>
 *   <li>Blank/empty → {@code EMPTY}</li>
 *   <li>Exceeds max URL length → {@code TOO_LONG}</li>
 *   <li>Format validation (scheme, host, TLD) → {@code INVALID_URL}</li>
 *   <li>Duplicate against URLs already in the UI work set → {@code DUPLICATE_IN_GRID}</li>
 *   <li>Duplicate within this submitted batch → {@code DUPLICATE_IN_BATCH}</li>
 *   <li>Existing shortlinks for this target URL → {@code HAS_EXISTING_SHORTLINKS}</li>
 *   <li>Otherwise → {@code VALID}</li>
 * </ol>
 *
 * <p>Server-side limits (see {@link BulkValidateRequest}):
 * <ul>
 *   <li>Maximum {@value BulkValidateRequest#MAX_URLS} URLs per request.</li>
 *   <li>Maximum {@value BulkValidateRequest#MAX_URL_LENGTH} characters per URL.</li>
 * </ul>
 */
@RequiresPermission("bulkValidateLinks")
public class BulkValidateHandler implements HttpHandler, HasLogger {

  private final UrlMappingStore store;

  public BulkValidateHandler(UrlMappingStore store) {
    this.store = store;
  }

  @Override
  public void handle(HttpExchange ex) throws IOException {
    logger().info("BulkValidateHandler - {}", ex.getRequestMethod());
    if (!RequestMethodUtils.requirePost(ex)) {
      return;
    }

    try {
      final String body = readBody(ex.getRequestBody());
      final BulkValidateRequest req = fromJson(body, BulkValidateRequest.class);

      if (req.getUrls() == null || req.getUrls().isEmpty()) {
        ErrorResponses.badRequest(ex, "Missing or empty 'urls' list");
        return;
      }
      if (req.getUrls().size() > BulkValidateRequest.MAX_URLS) {
        ErrorResponses.badRequest(ex,
            "Too many URLs: " + req.getUrls().size()
                + " exceeds limit of " + BulkValidateRequest.MAX_URLS);
        return;
      }

      // Build reverse-lookup: normalizedUrl → list<ExistingShortlinkInfo> (loaded once for the whole request)
      final Map<String, List<ExistingShortlinkInfo>> existingByUrl = buildExistingUrlIndex();

      // URLs currently in the UI work set (for DUPLICATE_IN_GRID detection)
      final Set<String> gridUrls = req.getExistingUrls() == null
          ? new HashSet<>()
          : req.getExistingUrls().stream()
              .filter(u -> u != null && !u.isBlank())
              .map(String::strip)
              .collect(Collectors.toCollection(HashSet::new));

      // Tracks normalized URLs already processed in this batch (for DUPLICATE_IN_BATCH)
      final Set<String> seenInBatch = new LinkedHashSet<>();

      final List<ValidationItemResult> results = new ArrayList<>(req.getUrls().size());

      for (int i = 0; i < req.getUrls().size(); i++) {
        final String raw = req.getUrls().get(i);
        final String url = raw == null ? "" : raw.strip();

        // ── blank ─────────────────────────────────────────────────────────────
        if (url.isBlank()) {
          results.add(ValidationItemResult.empty(i, raw));
          continue;
        }

        // ── length guard ──────────────────────────────────────────────────────
        if (url.length() > BulkValidateRequest.MAX_URL_LENGTH) {
          results.add(ValidationItemResult.tooLong(i, url));
          seenInBatch.add(url);
          continue;
        }

        // ── format validation ─────────────────────────────────────────────────
        final var validation = UrlValidator.validate(url);
        if (!validation.valid()) {
          results.add(ValidationItemResult.invalidUrl(i, url, validation.message()));
          seenInBatch.add(url);
          continue;
        }

        // ── duplicate in grid ─────────────────────────────────────────────────
        if (gridUrls.contains(url)) {
          results.add(ValidationItemResult.duplicateInGrid(i, url));
          seenInBatch.add(url);
          continue;
        }

        // ── protocol-variant duplicate in grid (http↔https of same URL) ─────
        final String counterpartUrl = swapProtocol(url);
        if (counterpartUrl != null && gridUrls.contains(counterpartUrl)) {
          results.add(ValidationItemResult.duplicateInGrid(i, url,
              "URL already in work set as its " + (url.startsWith("https://") ? "http" : "https")
                  + " counterpart: " + counterpartUrl));
          seenInBatch.add(url);
          continue;
        }

        // ── duplicate in batch ────────────────────────────────────────────────
        if (seenInBatch.contains(url)) {
          results.add(ValidationItemResult.duplicateInBatch(i, url));
          continue;
        }
        seenInBatch.add(url);

        // ── existing shortlinks check ─────────────────────────────────────────
        final List<ExistingShortlinkInfo> existing = existingByUrl.getOrDefault(url, List.of());
        if (!existing.isEmpty()) {
          results.add(ValidationItemResult.hasExisting(i, url, url, existing));
        } else {
          results.add(ValidationItemResult.valid(i, url, url));
        }
      }

      logger().info("BulkValidateHandler validated {} URLs – {} creatable, {} blocking",
          results.size(),
          results.stream().filter(ValidationItemResult::isCreatable).count(),
          results.stream().filter(ValidationItemResult::isBlocking).count());

      SuccessResponses.ok(ex, new BulkValidateResponse(results));

    } catch (IllegalArgumentException e) {
      logger().warn("bad request – {}", e.getMessage());
      ErrorResponses.badRequest(ex, e.getMessage());
    } catch (Exception e) {
      logger().error("internal error", e);
      ErrorResponses.internalServerError(ex, "internal_error");
    }
  }

  /**
   * Loads all mappings once and indexes them by their {@code originalUrl}.
   * This gives O(1) per-URL lookup during the validation loop.
   * Also adds protocol-variant entries (http↔https) marked with {@code protocolVariant=true}.
   */
  private Map<String, List<ExistingShortlinkInfo>> buildExistingUrlIndex() {
    final Map<String, List<ExistingShortlinkInfo>> index = new HashMap<>();
    try {
      for (var mapping : store.findAll()) {
        final ExistingShortlinkInfo info = new ExistingShortlinkInfo(
            mapping.shortCode(), mapping.active(), mapping.getExpiresAt(), false);
        index.computeIfAbsent(mapping.originalUrl(), _ -> new ArrayList<>()).add(info);

        final String counterpart = swapProtocol(mapping.originalUrl());
        if (counterpart != null) {
          final ExistingShortlinkInfo variantInfo = new ExistingShortlinkInfo(
              mapping.shortCode(), mapping.active(), mapping.getExpiresAt(), true);
          index.computeIfAbsent(counterpart, _ -> new ArrayList<>()).add(variantInfo);
        }
      }
    } catch (Exception e) {
      logger().warn("Could not load existing mappings for duplicate-check; proceeding without", e);
    }
    return index;
  }

  private static String swapProtocol(String url) {
    if (url == null) {
      return null;
    }
    if (url.startsWith("https://")) {
      return "http://" + url.substring(8);
    }
    if (url.startsWith("http://")) {
      return "https://" + url.substring(7);
    }
    return null;
  }
}
