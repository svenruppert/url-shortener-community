package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.core.prefs.ColumnDeleteRequest;
import com.svenruppert.urlshortener.core.prefs.ColumnEditRequest;
import com.svenruppert.urlshortener.core.prefs.ColumnInfoRequest;
import com.svenruppert.urlshortener.core.prefs.ColumnSingleEditRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static com.svenruppert.urlshortener.core.JsonUtils.parseJson;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Client for server-side column visibility preferences.
 * <p>
 * Endpoints:
 * - POST /admin/preferences/columns -> load
 * - DELETE /admin/preferences/columns -> delete all (for a view)
 * - PUT /admin/preferences/columns/edit -> bulk edit
 * - PUT /admin/preferences/columns/single -> single edit
 */
public final class ColumnVisibilityClient
    implements HasLogger {

  private final String baseUrl;      // e.g. "http://localhost:8080"
  private final HttpClient http;
  private java.util.function.Supplier<String> bearerToken = AuthContext::getGlobalToken;

  public ColumnVisibilityClient() {
    this(ADMIN_SERVER_URL);
  }

  public ColumnVisibilityClient(String baseUrl) {
    this(baseUrl, HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build());
  }

  public ColumnVisibilityClient(String baseUrl, HttpClient httpClient) {
    if (baseUrl.endsWith("/")) {
      this.baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    } else {
      this.baseUrl = baseUrl;
    }
    this.http = httpClient;
  }

  /** Session-scoped token source (e.g. Vaadin session) instead of the global default. */
  public ColumnVisibilityClient withBearerToken(java.util.function.Supplier<String> tokenSupplier) {
    this.bearerToken = tokenSupplier;
    return this;
  }

  private HttpRequest.BodyPublisher jsonBody(Object dto) {
    var json = toJson(dto);
    logger().info("jsonBody - json: {}", json);
    return HttpRequest.BodyPublishers.ofString(json, UTF_8);
  }

  public Map<String, Boolean> loadOrDefaultAllVisible(String userId, String viewId, Iterable<String> knownKeys)
      throws IOException, InterruptedException {
    var vis = load(userId, viewId);
    if (vis.isEmpty()) {
      var defaults = new LinkedHashMap<String, Boolean>();
      for (var k : knownKeys) defaults.put(k, Boolean.TRUE);
      return defaults;
    }
    return vis;
  }

  /**
   * Loads the visibility map for a given user & view. Returns an empty map if none is stored.
   */
  public Map<String, Boolean> load(String userId, String viewId)
      throws IOException, InterruptedException {
    var reqDto = new ColumnInfoRequest(userId, viewId);
    var req = requestBuilder(PATH_ADMIN_PREFERENCES_COLUMNS)
        .POST(jsonBody(reqDto))
        .build();

    var resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    if (resp.statusCode() == 200) {
      // Response is a flat JSON map: { "columnKey": true/false, ... }
      var body = resp.body();
      if (body == null || body.isBlank()) return Collections.emptyMap();
      var parsed = parseJson(body);
      return parsed.entrySet()
          .stream()
          .collect(Collectors.toMap(
              Map.Entry::getKey,
              e -> Boolean.parseBoolean(e.getValue())
          ));
    }
    if (resp.statusCode() == 204) {
      return Collections.emptyMap();
    }
    throw new IOException("Unexpected HTTP " + resp.statusCode() + " while loading column visibilities: " + resp.body());
  }

  /**
   * Bulk edit multiple columns. Idempotent.
   */
  public void editBulk(String userId, String viewId, Map<String, Boolean> changes)
      throws IOException, InterruptedException {
    if (changes == null || changes.isEmpty()) {
      throw new IllegalArgumentException("changes must not be empty");
    }
    var reqDto = new ColumnEditRequest(userId, viewId, changes);
    var bodyPublisher = jsonBody(reqDto);
    logger().info("bodyPublisher.contentLength: {}", bodyPublisher.contentLength());
    var req = requestBuilder(PATH_ADMIN_PREFERENCES_COLUMNS_EDIT)
        .PUT(bodyPublisher)
        .build();
    logger().info("editBulk - req(URI) {}", req.uri());
    var resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    if (resp.statusCode() != HttpStatus.NO_CONTENT.code()) {
      throw new IOException("Unexpected HTTP " + resp.statusCode() + " on bulk edit: " + resp.body());
    }
  }

  /**
   * Edit a single column key. Idempotent.
   */
  public void editSingle(String userId, String viewId, String columnKey, boolean visible)
      throws IOException, InterruptedException {
    var reqDto = new ColumnSingleEditRequest(userId, viewId, columnKey, visible);
    var req = requestBuilder(PATH_ADMIN_PREFERENCES_COLUMNS_SINGLE)
        .PUT(jsonBody(reqDto))
        .build();

    var resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    if (resp.statusCode() != 200) {
      throw new IOException("Unexpected HTTP " + resp.statusCode() + " on single edit: " + resp.body());
    }
  }

  /**
   * Delete all stored visibilities for a view. Idempotent; expects 204 No Content.
   */
  public void deleteAllForView(String userId, String viewId)
      throws IOException, InterruptedException {
    var reqDto = new ColumnInfoRequest(userId, viewId);
    var req = requestBuilder(PATH_ADMIN_PREFERENCES_COLUMNS)
        .method("DELETE", jsonBody(reqDto))
        .build();

    var resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    if (resp.statusCode() != 204) {
      throw new IOException("Unexpected HTTP " + resp.statusCode() + " on delete-all: " + resp.body());
    }
  }

  // ---------- helpers ----------

  /**
   * Delete a single column key for a view. Idempotent; expects 204 No Content.
   */
  public void deleteSingle(String userId, String viewId, String columnKey)
      throws IOException, InterruptedException {
    var reqDto = new ColumnDeleteRequest(userId, viewId, columnKey);
    var bodyPublisher = jsonBody(reqDto);
    var req = requestBuilder(PATH_ADMIN_PREFERENCES_COLUMNS_SINGLE) // -> "/admin/preferences/columns"
        .method("DELETE", bodyPublisher)
        .build();

    var resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    if (resp.statusCode() != 204) {
      throw new IOException("Unexpected HTTP " + resp.statusCode() + " on delete-single: " + resp.body());
    }
  }

  private HttpRequest.Builder requestBuilder(String path) {
    var builder = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + path))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .timeout(Duration.ofSeconds(10));
    var token = bearerToken.get();
    if (token != null && !token.isBlank()) {
      builder.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    }
    return builder;
  }


}
