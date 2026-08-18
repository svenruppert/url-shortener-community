package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignMetaExport;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V00.17.00 P040 — SEC-H3: the campaign-meta export/import boundary. Before
 * this cycle, GET /api/campaigns/export dumped every user's campaign metadata
 * and POST /api/campaigns/import let any USER overwrite (or with replace=true
 * wipe) all of it. Real in-process server, seeded admin/user plus a third
 * regular user created via the admin API.
 */
class CampaignMetaBoundaryE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;
  private static String ownerToken;
  private static String attackerToken;

  private static String ownedCode;
  private static String ownedCampaignId;

  @BeforeAll
  static void startServer()
      throws Exception {
    com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider
        .setDirectorySupplier(
            com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory::new);
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    baseUrlAdmin = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    adminToken = login("admin", "admin");
    ownerToken = login("user", "user");
    var created = send(request(PATH_ADMIN_USERS, adminToken)
        .POST(body(JsonUtils.toJson(Map.of(
            "username", "eve2", "password", "eve-secret-2", "roles", List.of("USER")))))
        .build());
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    attackerToken = login("eve2", "eve-secret-2");

    // Owner fixture: one link in one campaign with a tag.
    var linkRes = send(request(PATH_ADMIN_SHORTEN, ownerToken)
        .POST(body(JsonUtils.toJson(Map.of("url", "https://example.org/h3")))).build());
    assertEquals(CREATED.code(), linkRes.statusCode(), linkRes.body());
    ownedCode = JsonUtils.fromJson(linkRes.body(), ShortUrlMapping.class).shortCode();

    var campRes = send(request(PATH_ADMIN_CAMPAIGNS, ownerToken)
        .POST(body(JsonUtils.toJson(Map.of("name", "h3-campaign")))).build());
    assertEquals(CREATED.code(), campRes.statusCode(), campRes.body());
    ownedCampaignId = JsonUtils.fromJson(campRes.body(), Campaign.class).id();

    assertEquals(NO_CONTENT.code(),
        send(request(PATH_ADMIN_CAMPAIGNS + "/" + ownedCampaignId + "/links/" + ownedCode, ownerToken)
            .POST(HttpRequest.BodyPublishers.noBody()).build()).statusCode());
    assertEquals(NO_CONTENT.code(),
        send(request(PATH_ADMIN_LINK_META + "/" + ownedCode + "/tags", ownerToken)
            .PUT(body("{\"tags\":[\"h3tag\"]}")).build()).statusCode());
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  // --- helpers --------------------------------------------------------------

  private static String login(String username, String password)
      throws Exception {
    var res = send(request(PATH_AUTH_LOGIN, null)
        .POST(body(JsonUtils.toJson(Map.of("username", username, "password", password)))).build());
    assertEquals(OK.code(), res.statusCode(), res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }

  private static HttpRequest.Builder request(String path, String token) {
    var b = HttpRequest.newBuilder(URI.create(baseUrlAdmin + path))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8());
    if (token != null) b.header("Authorization", "Bearer " + token);
    return b;
  }

  private static HttpRequest.BodyPublisher body(String json) {
    return HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
  }

  private static HttpResponse<String> send(HttpRequest request)
      throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static CampaignMetaExport export(String token)
      throws Exception {
    var res = send(request(PATH_ADMIN_CAMPAIGNS + "/export", token).GET().build());
    assertEquals(OK.code(), res.statusCode(), res.body());
    return JsonUtils.fromJson(res.body(), CampaignMetaExport.class);
  }

  // --- tests ----------------------------------------------------------------

  @Test
  void exportIsOwnerScoped()
      throws Exception {
    CampaignMetaExport attackerView = export(attackerToken);
    assertTrue(attackerView.campaigns().stream().noneMatch(c -> c.id().equals(ownedCampaignId)),
        "a foreign USER must not see the owner's campaign in the export (SEC-H3)");
    assertFalse(attackerView.campaignOfLink().containsKey(ownedCode),
        "a foreign USER must not see the owner's link membership");
    assertFalse(attackerView.linkTags().containsKey(ownedCode),
        "a foreign USER must not see the owner's link tags");

    CampaignMetaExport ownerView = export(ownerToken);
    assertTrue(ownerView.campaigns().stream().anyMatch(c -> c.id().equals(ownedCampaignId)),
        "the owner must keep seeing their campaign");
    assertEquals(ownedCampaignId, ownerView.campaignOfLink().get(ownedCode));

    CampaignMetaExport adminView = export(adminToken);
    assertTrue(adminView.campaigns().stream().anyMatch(c -> c.id().equals(ownedCampaignId)),
        "link:read:all keeps the full export");
  }

  @Test
  void replaceImportIsAdminOnly()
      throws Exception {
    var wipe = send(request(PATH_ADMIN_CAMPAIGNS + "/import?replace=true", attackerToken)
        .POST(body("{\"formatVersion\":\"2\",\"campaigns\":[],\"campaignOfLink\":{},"
            + "\"linkTags\":{},\"linkLabels\":{},\"knownTags\":[]}")).build());
    assertEquals(FORBIDDEN.code(), wipe.statusCode(),
        "the destructive replace import must be admin-only (SEC-H3)");

    assertEquals(ownedCampaignId, export(ownerToken).campaignOfLink().get(ownedCode),
        "the owner's data must be untouched after the rejected wipe");
  }

  @Test
  void mergeImportCannotTouchForeignData()
      throws Exception {
    String payload = JsonUtils.toJson(new CampaignMetaExport(
        CampaignMetaExport.FORMAT_VERSION,
        List.of(new Campaign(ownedCampaignId, "hijacked-name", null, null, null, null, null, null)),
        Map.of(ownedCode, ownedCampaignId),
        Map.of(ownedCode, List.of("evil-tag")),
        Map.of(ownedCode, "evil-label"),
        List.of()));
    var merge = send(request(PATH_ADMIN_CAMPAIGNS + "/import", attackerToken)
        .POST(body(payload)).build());
    assertEquals(NO_CONTENT.code(), merge.statusCode(),
        "merge import itself stays allowed — but scoped: " + merge.body());

    var campaign = send(request(PATH_ADMIN_CAMPAIGNS + "/" + ownedCampaignId, ownerToken).GET().build());
    assertEquals(OK.code(), campaign.statusCode());
    assertEquals("h3-campaign", JsonUtils.fromJson(campaign.body(), Campaign.class).name(),
        "a foreign merge import must not rename the owner's campaign (SEC-H3)");

    var meta = send(request(PATH_ADMIN_LINK_META + "/" + ownedCode, ownerToken).GET().build());
    assertEquals(OK.code(), meta.statusCode());
    assertFalse(meta.body().contains("evil-tag"),
        "a foreign merge import must not alter the owner's link tags");
    assertFalse(meta.body().contains("evil-label"),
        "a foreign merge import must not alter the owner's link label");
  }
}
