package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignCreateRequest;
import com.svenruppert.urlshortener.core.campaign.AssignTagRequest;
import com.svenruppert.urlshortener.core.campaign.BatchAssignRequest;
import com.svenruppert.urlshortener.core.campaign.BatchAssignResponse;
import com.svenruppert.urlshortener.core.campaign.CampaignMetaExport;
import com.svenruppert.urlshortener.core.campaign.CampaignStatisticsResponse;
import com.svenruppert.urlshortener.core.campaign.CreateTagRequest;
import com.svenruppert.urlshortener.core.campaign.DeleteTagRequest;
import com.svenruppert.urlshortener.core.campaign.LinkMetaResponse;
import com.svenruppert.urlshortener.core.campaign.RenameTagRequest;
import com.svenruppert.urlshortener.core.campaign.ReplaceTagRequest;
import com.svenruppert.urlshortener.core.campaign.SetLabelRequest;
import com.svenruppert.urlshortener.core.campaign.SetTagsRequest;
import com.svenruppert.urlshortener.core.campaign.TagInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;
import static com.svenruppert.urlshortener.core.DefaultValues.API_KEY_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_CAMPAIGNS;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_LINK_META;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_TAGS;

/**
 * Client for the campaign endpoints: campaign CRUD, link assignment, per-link
 * tags/label, and campaign/tag rollup statistics. Bearer-authenticated like
 * {@link UsersClient}.
 */
public class CampaignClient
    implements HasLogger {

  private final String baseUrl;
  private final HttpClient http;
  private Supplier<String> bearerToken = AuthContext::getGlobalToken;
  private Supplier<String> apiKey = () -> null;

  public CampaignClient() {
    this(ADMIN_SERVER_URL);
  }

  public CampaignClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public CampaignClient withBearerToken(Supplier<String> tokenSupplier) {
    this.bearerToken = tokenSupplier;
    return this;
  }

  /** Session-scoped API-key source; sent as {@code X-API-Key} (used by the MCP pass-through). */
  public CampaignClient withApiKey(Supplier<String> apiKeySupplier) {
    this.apiKey = apiKeySupplier;
    return this;
  }

  public Result<Campaign, String> create(CampaignCreateRequest request) {
    return send(post(PATH_ADMIN_CAMPAIGNS, JacksonJson.toJson(request)), HttpStatus.CREATED,
        body -> JacksonJson.fromJson(body, Campaign.class));
  }

  public Result<Campaign, String> get(String campaignId) {
    return send(getReq(PATH_ADMIN_CAMPAIGNS + "/" + campaignId), HttpStatus.OK,
        body -> JacksonJson.fromJson(body, Campaign.class));
  }

  public Result<List<Campaign>, String> list() {
    return send(getReq(PATH_ADMIN_CAMPAIGNS), HttpStatus.OK,
        body -> List.of(JacksonJson.fromJson(body, Campaign[].class)));
  }

  public Result<Boolean, String> delete(String campaignId) {
    return send(builder(PATH_ADMIN_CAMPAIGNS + "/" + campaignId).DELETE().build(),
        HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  public Result<Boolean, String> assignLink(String campaignId, String shortCode) {
    return send(builder(PATH_ADMIN_CAMPAIGNS + "/" + campaignId + "/links/" + shortCode)
        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  public Result<Boolean, String> removeLink(String campaignId, String shortCode) {
    return send(builder(PATH_ADMIN_CAMPAIGNS + "/" + campaignId + "/links/" + shortCode)
        .DELETE().build(), HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  /**
   * Assigns several links to a campaign in one request, with an optional shared
   * placement label. Links the caller cannot manage are skipped.
   */
  public Result<BatchAssignResponse, String> assignLinks(String campaignId, List<String> shortCodes, String label) {
    return send(post(PATH_ADMIN_CAMPAIGNS + "/" + campaignId + "/links",
        JacksonJson.toJson(new BatchAssignRequest(shortCodes, label))), HttpStatus.OK,
        body -> JacksonJson.fromJson(body, BatchAssignResponse.class));
  }

  public Result<CampaignStatisticsResponse, String> campaignStatistics(String campaignId, LocalDate from, LocalDate to) {
    return send(getReq(PATH_ADMIN_CAMPAIGNS + "/" + campaignId + "/statistics?from=" + from + "&to=" + to),
        HttpStatus.OK, body -> JacksonJson.fromJson(body, CampaignStatisticsResponse.class));
  }

  public Result<CampaignStatisticsResponse, String> statisticsByTag(String tag, LocalDate from, LocalDate to) {
    return send(getReq(PATH_ADMIN_CAMPAIGNS + "/tags/" + tag + "/statistics?from=" + from + "&to=" + to),
        HttpStatus.OK, body -> JacksonJson.fromJson(body, CampaignStatisticsResponse.class));
  }

  public Result<Boolean, String> setTags(String shortCode, List<String> tags) {
    return send(put(PATH_ADMIN_LINK_META + "/" + shortCode + "/tags",
        JacksonJson.toJson(new SetTagsRequest(tags))), HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  public Result<Boolean, String> setLabel(String shortCode, String label) {
    return send(put(PATH_ADMIN_LINK_META + "/" + shortCode + "/label",
        JacksonJson.toJson(new SetLabelRequest(label))), HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  /** Returns the raw link-meta JSON ({@code {shortCode, campaignId, tags, label}}). */
  public Result<String, String> linkMeta(String shortCode) {
    return send(getReq(PATH_ADMIN_LINK_META + "/" + shortCode), HttpStatus.OK, body -> body);
  }

  /** Typed link-meta: campaign membership, tags and placement label of a link. */
  public Result<LinkMetaResponse, String> linkMetaTyped(String shortCode) {
    return send(getReq(PATH_ADMIN_LINK_META + "/" + shortCode), HttpStatus.OK,
        body -> JacksonJson.fromJson(body, LinkMetaResponse.class));
  }

  /** Exports a snapshot of all campaign metadata (definitions, membership, tags, labels). */
  public Result<CampaignMetaExport, String> exportMeta() {
    return send(getReq(PATH_ADMIN_CAMPAIGNS + "/export"), HttpStatus.OK,
        body -> JacksonJson.fromJson(body, CampaignMetaExport.class));
  }

  /** Imports a campaign-metadata snapshot ({@code replace} clears existing metadata first). */
  public Result<Boolean, String> importMeta(CampaignMetaExport data, boolean replace) {
    return send(post(PATH_ADMIN_CAMPAIGNS + "/import?replace=" + replace, JacksonJson.toJson(data)),
        HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  /** Lists all distinct tags with their link counts. */
  public Result<List<TagInfo>, String> listTags() {
    return send(getReq(PATH_ADMIN_TAGS), HttpStatus.OK,
        body -> List.of(JacksonJson.fromJson(body, TagInfo[].class)));
  }

  /** Defines a tag in the registry (empty), so it can be assigned to links later. */
  public Result<Boolean, String> createTag(String name) {
    return send(post(PATH_ADMIN_TAGS + "/create", JacksonJson.toJson(new CreateTagRequest(name))),
        HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  /** Adds a tag to several links at once (union with their existing tags). */
  public Result<Boolean, String> assignTagToLinks(String tag, List<String> shortCodes) {
    return send(post(PATH_ADMIN_TAGS + "/assign", JacksonJson.toJson(new AssignTagRequest(tag, shortCodes))),
        HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  /** Renames a tag across every link (merges into {@code to} if it already exists). */
  public Result<Boolean, String> renameTag(String from, String to) {
    return send(post(PATH_ADMIN_TAGS + "/rename", JacksonJson.toJson(new RenameTagRequest(from, to))),
        HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  /** Replaces a tag by one or more others across every link that carries it. */
  public Result<Boolean, String> replaceTag(String from, List<String> to) {
    return send(post(PATH_ADMIN_TAGS + "/replace", JacksonJson.toJson(new ReplaceTagRequest(from, to))),
        HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  /** Removes a tag from every link that carries it. */
  public Result<Boolean, String> deleteTag(String tag) {
    return send(post(PATH_ADMIN_TAGS + "/delete", JacksonJson.toJson(new DeleteTagRequest(tag))),
        HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  private HttpRequest getReq(String path) {
    return builder(path).GET().build();
  }

  private HttpRequest post(String path, String json) {
    return builder(path)
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
  }

  private HttpRequest put(String path, String json) {
    return builder(path)
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .PUT(HttpRequest.BodyPublishers.ofString(json))
        .build();
  }

  private HttpRequest.Builder builder(String path) {
    var b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10));
    var token = bearerToken.get();
    if (token != null && !token.isBlank()) {
      b.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    }
    var key = apiKey.get();
    if (key != null && !key.isBlank()) {
      b.header(API_KEY_HEADER, key);
    }
    return b;
  }

  private <T> Result<T, String> send(HttpRequest request, HttpStatus expected, Function<String, T> parser) {
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != expected.code()) {
        return Result.failure("Unexpected status " + response.statusCode() + ": " + response.body());
      }
      return Result.success(parser.apply(response.body()));
    } catch (IOException e) {
      logger().warn("I/O error calling {}: {}", request.uri(), e.getMessage());
      return Result.failure("I/O error: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failure("Interrupted");
    }
  }
}
