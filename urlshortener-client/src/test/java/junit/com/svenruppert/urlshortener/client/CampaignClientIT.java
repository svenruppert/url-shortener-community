package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.AuthContext;
import com.svenruppert.urlshortener.client.CampaignClient;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignCreateRequest;
import com.svenruppert.urlshortener.core.campaign.CampaignStatisticsResponse;
import com.svenruppert.urlshortener.core.campaign.CampaignType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks IT for the {@link CampaignClient} SDK against the real in-process
 * server: create campaign, assign an admin-owned link, set label/tags, drive
 * real redirects, then read the bot-excluded rollup via the SDK.
 */
class CampaignClientIT {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2030, 1, 1);

  private ShortenerServer server;
  private String adminUrl;
  private String redirectUrl;
  private CampaignClient cc;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void start() throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    redirectUrl = "http://" + DEFAULT_SERVER_HOST + ":" + server.getPortRedirect();
    TestAuth.loginAsAdmin(adminUrl); // installs the bearer token as the SDK default
    cc = new CampaignClient(adminUrl);
  }

  @AfterEach
  void stop() {
    server.shutdown();
    TestAuth.clear();
  }

  @Test
  void sdk_fullLifecycle() throws Exception {
    shorten("camp-b", "https://example.com/b"); // admin-owned link

    Campaign c = cc.create(new CampaignCreateRequest("iX 07/2026", "issue", CampaignType.MAGAZINE, null, null))
        .getOrThrow();
    assertTrue(c.id().startsWith("cmp_"));

    assertTrue(cc.assignLink(c.id(), "camp-b").getOrThrow());
    assertTrue(cc.setLabel("camp-b", "Cover-QR").getOrThrow());
    assertTrue(cc.setTags("camp-b", List.of("Print", "ix")).getOrThrow());

    redirect("camp-b", "Mozilla/5.0 Chrome/124");
    redirect("camp-b", "Mozilla/5.0 Safari/604");
    redirect("camp-b", "TelegramBot");

    CampaignStatisticsResponse stats = cc.campaignStatistics(c.id(), FROM, TO).getOrThrow();
    assertEquals(2, stats.total(), "bot excluded");
    assertEquals(2, stats.timeline().stream().mapToLong(CampaignStatisticsResponse.DayCount::count).sum(),
        "timeline (the campaign chart data) sums to the human total");
    assertEquals("camp-b", stats.perLink().getFirst().shortCode());
    assertEquals("Cover-QR", stats.perLink().getFirst().label());

    assertEquals(2, cc.statisticsByTag("ix", FROM, TO).getOrThrow().total());

    assertTrue(cc.list().getOrThrow().stream().anyMatch(x -> x.id().equals(c.id())));
    assertTrue(cc.linkMeta("camp-b").getOrThrow().contains(c.id()));

    assertTrue(cc.delete(c.id()).getOrThrow());
    assertTrue(cc.get(c.id()).isFailure(), "deleted -> 404");
  }

  private void shorten(String alias, String url) throws Exception {
    var req = HttpRequest.newBuilder(URI.create(adminUrl + "/api/shorten"))
        .header("Authorization", "Bearer " + AuthContext.getGlobalToken())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(
            "{\"url\":\"" + url + "\",\"shortURL\":\"" + alias + "\"}", UTF_8))
        .build();
    http.send(req, HttpResponse.BodyHandlers.discarding());
  }

  private void redirect(String code, String userAgent) throws Exception {
    var req = HttpRequest.newBuilder(URI.create(redirectUrl + "/" + code))
        .header("User-Agent", userAgent).GET().build();
    http.send(req, HttpResponse.BodyHandlers.discarding());
  }
}
