package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.CampaignClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignCreateRequest;
import com.svenruppert.urlshortener.core.campaign.CampaignType;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks IT for atomic assign-at-create (P020/P030): a single {@code
 * createCustomMapping} and a {@code bulkShorten} carry campaign id, tags and a
 * placement label that the server applies right after creation. Verified via the
 * link-meta endpoint, the tag filter and the campaign rollup — all against the
 * real in-process server.
 */
class AssignAtCreateIT {

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
  void singleAndBulkAssignAtCreate() throws Exception {
    Campaign c = cc.create(new CampaignCreateRequest("iX 07/2026", "issue", CampaignType.MAGAZINE, null, null))
        .getOrThrow();

    // ── single create carries campaign + tags + label ──────────────────────────
    client.createCustomMapping("aac-1", "https://example.com/1", null, null,
        c.id(), List.of("Print", "ix"), "Cover-QR");

    String meta = cc.linkMeta("aac-1").getOrThrow();
    assertTrue(meta.contains(c.id()), "campaign assigned at create");
    assertTrue(meta.contains("print"), "tag normalized + assigned");
    assertTrue(meta.contains("ix"), "second tag assigned");
    assertTrue(meta.contains("Cover-QR"), "label assigned");

    // typed link-meta (the shape the UI's DetailsDialog consumes) deserializes correctly
    var typed = cc.linkMetaTyped("aac-1").getOrThrow();
    assertEquals(c.id(), typed.campaignId());
    assertEquals(Set.of("print", "ix"), new java.util.HashSet<>(typed.tags()));
    assertEquals("Cover-QR", typed.label());

    // ── bulk create applies the batch-wide metadata to every link ──────────────
    client.bulkShorten(List.of("https://example.com/2", "https://example.com/3"),
        null, null, c.id(), List.of("ix"), "Batch");

    // three links now carry tag "ix" (one single + two bulk)
    assertEquals(3, client.listCount(UrlMappingListRequest.builder().tag("ix").build()));

    // and all three are members of the campaign (rollup lists every member)
    assertEquals(3, cc.campaignStatistics(c.id(), FROM, TO).getOrThrow().perLink().size());

    // omitting metadata assigns nothing
    client.createCustomMapping("plain-1", "https://example.com/9", null, null);
    String plainMeta = cc.linkMeta("plain-1").getOrThrow();
    assertTrue(plainMeta.contains("\"campaignId\":null") || !plainMeta.contains(c.id()),
        "no campaign when omitted");
  }
}
