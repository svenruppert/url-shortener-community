package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.AuthContext;
import com.svenruppert.urlshortener.core.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks E2E for the campaign REST API against the real in-process server:
 * create a campaign, assign an admin-owned link, set label/tags, drive real
 * redirects (2 human + 1 bot), then verify the rollup excludes the bot and
 * carries the placement label. Also the tag rollup and delete.
 */
class CampaignApiIT {

  private ShortenerServer server;
  private String adminUrl;
  private String redirectUrl;
  private String token;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void start() throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    redirectUrl = "http://" + DEFAULT_SERVER_HOST + ":" + server.getPortRedirect();
    TestAuth.loginAsAdmin(adminUrl);
    token = AuthContext.getGlobalToken();
  }

  @AfterEach
  void stop() {
    server.shutdown();
    TestAuth.clear();
  }

  @Test
  void campaignLifecycle_rollupExcludesBots_withPlacementLabel() throws Exception {
    // admin-owned link
    assertTrue(send("POST", adminUrl + "/api/shorten",
        "{\"url\":\"https://example.com/a\",\"shortURL\":\"camp-a\"}").statusCode() < 300);

    // create campaign
    var created = send("POST", adminUrl + "/api/campaigns",
        "{\"name\":\"iX 07/2026\",\"type\":\"MAGAZINE\"}");
    assertEquals(201, created.statusCode(), created.body());
    String campaignId = JsonUtils.parseJson(created.body()).get("id");
    assertTrue(campaignId != null && campaignId.startsWith("cmp_"), created.body());

    // assign + label + tags
    assertEquals(204, send("POST", adminUrl + "/api/campaigns/" + campaignId + "/links/camp-a", null).statusCode());
    assertEquals(204, send("PUT", adminUrl + "/api/link-meta/camp-a/label", "{\"label\":\"Cover-QR\"}").statusCode());
    assertEquals(204, send("PUT", adminUrl + "/api/link-meta/camp-a/tags", "{\"tags\":[\"Print\",\"ix\"]}").statusCode());

    // real redirects: 2 human + 1 bot (bot must not count)
    redirect("camp-a", "Mozilla/5.0 (Macintosh) Chrome/124");
    redirect("camp-a", "Mozilla/5.0 (iPhone) Safari/604");
    redirect("camp-a", "TelegramBot (like TwitterBot)");

    // campaign rollup
    var stats = send("GET", adminUrl + "/api/campaigns/" + campaignId
        + "/statistics?from=2026-01-01&to=2030-01-01", null);
    assertEquals(200, stats.statusCode(), stats.body());
    assertTrue(stats.body().contains("\"total\":2"), "bot excluded -> total 2: " + stats.body());
    assertTrue(stats.body().contains("Cover-QR"), "placement label present: " + stats.body());
    assertTrue(stats.body().contains("camp-a"), stats.body());

    // tag rollup
    var byTag = send("GET", adminUrl + "/api/campaigns/tags/ix/statistics?from=2026-01-01&to=2030-01-01", null);
    assertEquals(200, byTag.statusCode(), byTag.body());
    assertTrue(byTag.body().contains("\"total\":2"), byTag.body());

    // link-meta read
    var meta = send("GET", adminUrl + "/api/link-meta/camp-a", null);
    assertEquals(200, meta.statusCode());
    assertTrue(meta.body().contains(campaignId) && meta.body().contains("Cover-QR"), meta.body());

    // delete
    assertEquals(204, send("DELETE", adminUrl + "/api/campaigns/" + campaignId, null).statusCode());
    assertEquals(404, send("GET", adminUrl + "/api/campaigns/" + campaignId, null).statusCode());
  }

  private HttpResponse<String> send(String method, String url, String body) throws Exception {
    var b = HttpRequest.newBuilder(URI.create(url))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "application/json")
        .method(method, body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, UTF_8));
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
  }

  private void redirect(String code, String userAgent) throws Exception {
    var req = HttpRequest.newBuilder(URI.create(redirectUrl + "/" + code))
        .header("User-Agent", userAgent)
        .GET().build();
    http.send(req, HttpResponse.BodyHandlers.discarding()); // 302, not followed
  }
}
