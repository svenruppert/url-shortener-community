package com.svenruppert.urlshortener.api.handler.urlmapping.imports;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import com.svenruppert.urlshortener.api.store.imports.ImportStaging;
import com.svenruppert.urlshortener.api.store.imports.ImportStagingStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.validation.UrlValidator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.svenruppert.urlshortener.api.handler.urlmapping.imports.ZipImportReader.extractExportJson;
import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBodyBytes;
import static com.svenruppert.urlshortener.core.DefaultValues.*;


@RequiresPermission("importValidate")
public final class ImportValidateHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingStore store;
  private final ImportStagingStore stagingStore;

  public ImportValidateHandler(UrlMappingStore store, ImportStagingStore stagingStore) {
    this.store = store;
    this.stagingStore = stagingStore;
  }

  private static boolean same(ShortUrlMapping a, ShortUrlMapping b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    return eq(a.shortCode(), b.shortCode())
        && eq(a.originalUrl(), b.originalUrl())
        && eq(a.createdAt(), b.createdAt())
        && eq(a.expiresAt().orElse(null), b.expiresAt().orElse(null))
        && a.active() == b.active();
  }

  private static boolean eq(Object a, Object b) {
    return Objects.equals(a, b);
  }

  private static String safeTrim(String s) {
    return s == null ? null : s.trim();
  }

  /**
   * SEC-L4 (V00.17.00 P070): full JSON string escaping — backslash/quote alone
   * left control characters unescaped, so an imported shortCode containing a
   * raw newline produced structurally broken preview JSON.
   */
  private static String escape(String v) {
    return v == null ? "" : com.svenruppert.urlshortener.api.utils.JsonWriter.escapeJsonString(v);
  }

  private static String buildPreviewFullJson(String stagingId, ImportStaging s) {
    StringBuilder sb = new StringBuilder(512);
    sb.append("{");
    sb.append("\"stagingId\":\"").append(escape(stagingId)).append("\",");
    sb.append("\"newItems\":").append(s.newItems().size()).append(",");
    sb.append("\"conflicts\":").append(s.conflicts().size()).append(",");
    sb.append("\"invalid\":").append(s.invalidItems().size()).append(",");

    sb.append("\"conflictItems\":").append(toJsonConflictItems(s.conflicts())).append(",");
    sb.append("\"invalidItems\":").append(toJsonInvalidItems(s.invalidItems()));

    sb.append("}");
    return sb.toString();
  }

  private static String toJsonConflictItems(List<ImportStaging.Conflict> conflicts) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean first = true;
    for (ImportStaging.Conflict c : conflicts) {
      if (!first) sb.append(",");
      first = false;
      sb.append(toJsonConflictRow(c));
    }
    sb.append("]");
    return sb.toString();
  }

  private static String toJsonConflictRow(ImportStaging.Conflict c) {
    var ex = c.existing();
    var in = c.incoming();

    String existingExpiresAt = ex.expiresAt().orElse(null) != null ? ex.expiresAt().get().toString() : "";
    String incomingExpiresAt = in.expiresAt().orElse(null) != null ? in.expiresAt().get().toString() : "";

    return "{"
        + "\"shortCode\":\"" + escape(c.shortCode()) + "\","
        + "\"existingUrl\":\"" + escape(ex.originalUrl()) + "\","
        + "\"incomingUrl\":\"" + escape(in.originalUrl()) + "\","
        + "\"existingActive\":\"" + ex.active() + "\","
        + "\"incomingActive\":\"" + in.active() + "\","
        + "\"existingExpiresAt\":\"" + escape(existingExpiresAt) + "\","
        + "\"incomingExpiresAt\":\"" + escape(incomingExpiresAt) + "\","
        + "\"diff\":\"" + escape(diff(ex, in)) + "\""
        + "}";
  }

  private static String diff(ShortUrlMapping a, ShortUrlMapping b) {
    List<String> diffs = new ArrayList<>();
    if (!eq(a.originalUrl(), b.originalUrl())) diffs.add("URL");
    if (!eq(a.createdAt(), b.createdAt())) diffs.add("CREATED_AT");
    if (!eq(a.expiresAt().orElse(null), b.expiresAt().orElse(null))) diffs.add("EXPIRES_AT");
    if (a.active() != b.active()) diffs.add("ACTIVE");
    return diffs.isEmpty() ? "NONE" : String.join("|", diffs);
  }

  private static String toJsonInvalidItems(List<ImportStaging.InvalidItem> invalid) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean first = true;
    for (ImportStaging.InvalidItem i : invalid) {
      if (!first) sb.append(",");
      first = false;
      sb.append("{")
          .append("\"shortCode\":\"").append(escape(i.shortCode() == null ? "" : i.shortCode())).append("\",")
          .append("\"reason\":\"").append(escape(i.reason())).append("\"")
          .append("}");
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePost(ex)) return;

    try {
      byte[] zipBytes = readBodyBytes(ex.getRequestBody(), IMPORT_MAX_ZIP_BYTES);

      InputStream jsonStream = extractExportJson(
          zipBytes,
          EXPORT_FILE_NAME + ".json",
          IMPORT_MAX_JSON_BYTES
      );

      ImportStaging staging = validate(jsonStream);
      String stagingId = stagingStore.put(staging);

      String body = buildPreviewFullJson(stagingId, staging);
      SuccessResponses.okJson(ex, body);

    } catch (Exception e) {
      logger().warn("Import validate failed", e);
      ErrorResponses.badRequest(ex, String.valueOf(e.getMessage()));
    }
  }

  private ImportStaging validate(InputStream exportJson)
      throws IOException {
    logger().info("start validating..");
    List<ShortUrlMapping> newItems = new ArrayList<>();
    List<ImportStaging.Conflict> conflicts = new ArrayList<>();
    List<ImportStaging.InvalidItem> invalid = new ArrayList<>();

    try (Reader r = new BufferedReader(new InputStreamReader(exportJson, StandardCharsets.UTF_8))) {
      for (String obj : new ItemsJsonIterator(r)) {
        ShortUrlMapping incoming;
        try {
          incoming = JsonUtils.fromJson(obj, ShortUrlMapping.class);
          logger().info("validate - {}", incoming);
        } catch (Exception ex) {
          invalid.add(new ImportStaging.InvalidItem(null, "JSON parse failed"));
          logger().warn("validate - error - {}", ex.getMessage());
          continue;
        }
        String code = safeTrim(incoming.shortCode());
        logger().info("validate - code - {}", code);
        if (code == null || code.isBlank()) {
          invalid.add(new ImportStaging.InvalidItem(null, "shortCode missing"));
          logger().warn("validate - code is blank or null");
          continue;
        }

        String url = safeTrim(incoming.originalUrl());
        logger().info("validate - url - {}", url);
        var vr = UrlValidator.validate(url);
        if (!vr.valid()) {
          invalid.add(new ImportStaging.InvalidItem(code, "invalid url: " + vr.message()));
          logger().warn("validate - url is not valid");
          continue;
        }

        Optional<ShortUrlMapping> existing = store.findByShortCode(code);
        logger().warn("validate - existing UrlShortCodeMapping {}", existing);
        if (existing.isEmpty()) {
          newItems.add(incoming);
          logger().info("validate - no existing mapping found, added new item from export {}", incoming);
        } else {
          ShortUrlMapping exMap = existing.get();
          if (!same(exMap, incoming)) {
            logger().info("validate - we found a conflict {} / {}", exMap, incoming);
            conflicts.add(new ImportStaging.Conflict(code, exMap, incoming));
          } else {
            logger().info("validate - both are the same..skipping..");
          }
        }
      }
    }
    logger().info("stop validating..");
    logger().info("validate - newItems {}", newItems);
    logger().info("validate - conflicts {}", conflicts);
    logger().info("validate - invalid {}", invalid);
    return new ImportStaging(Instant.now(), newItems, conflicts, invalid);
  }

}
