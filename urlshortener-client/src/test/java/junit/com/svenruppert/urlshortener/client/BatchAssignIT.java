package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.CampaignClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.campaign.BatchAssignResponse;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignCreateRequest;
import com.svenruppert.urlshortener.core.campaign.CampaignType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks IT for batch link assignment (Q2): several links assigned to a
 * campaign in one request with a shared placement label, verified via the
 * campaign rollup and typed link-meta against the real in-process server.
 */
class BatchAssignIT {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2030, 1, 1);

  private ShortenerServer server;
  private URLShortenerClient client;
  private CampaignClient cc;

  @BeforeEach
  void start() throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    String adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    String redirectUrl = "http://" + DEFAULT_SERVER_HOST + ":" + server.getPortRedirect();
    TestAuth.loginAsAdmin(adminUrl);
    client = new URLShortenerClient(adminUrl, redirectUrl);
    cc = new CampaignClient(adminUrl);
  }

  @AfterEach
  void stop() {
    server.shutdown();
    TestAuth.clear();
  }

  @Test
  void assignsManyLinksInOneRequest() throws Exception {
    client.createCustomMapping("ba-1", "https://example.com/1", null, null);
    client.createCustomMapping("ba-2", "https://example.com/2", null, null);
    client.createCustomMapping("ba-3", "https://example.com/3", null, null);

    Campaign c = cc.create(new CampaignCreateRequest("batch", null, CampaignType.OTHER, null, null)).getOrThrow();

    BatchAssignResponse resp = cc.assignLinks(c.id(), List.of("ba-1", "ba-2", "ba-3"), "Cover").getOrThrow();
    assertEquals(3, resp.assigned());
    assertEquals(0, resp.skipped());

    // all three are members and carry the shared label
    assertEquals(3, cc.campaignStatistics(c.id(), FROM, TO).getOrThrow().perLink().size());
    var meta = cc.linkMetaTyped("ba-2").getOrThrow();
    assertEquals(c.id(), meta.campaignId());
    assertEquals("Cover", meta.label());

    // blank codes are skipped, not assigned
    BatchAssignResponse withBlank = cc.assignLinks(c.id(), List.of("ba-1", "", "  "), null).getOrThrow();
    assertEquals(1, withBlank.assigned());
    assertEquals(2, withBlank.skipped());
    assertTrue(cc.delete(c.id()).getOrThrow());
  }
}
