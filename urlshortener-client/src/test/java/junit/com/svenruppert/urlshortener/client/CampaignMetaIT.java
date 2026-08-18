package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.CampaignClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignCreateRequest;
import com.svenruppert.urlshortener.core.campaign.CampaignMetaExport;
import com.svenruppert.urlshortener.core.campaign.CampaignType;
import com.svenruppert.urlshortener.core.campaign.TagInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks IT for the campaign-metadata export/import round-trip (Q4): campaign
 * definitions, per-link membership, tags and placement labels are exported from
 * one server and restored losslessly into a fresh server — all three levels.
 */
class CampaignMetaIT {

  @Test
  void exportImportRoundTrip() throws Exception {
    TestAuth.useSeededInMemoryDirectory();

    // ── source server: build campaign metadata, then export ────────────────────
    String campaignId;
    CampaignMetaExport snapshot;
    ShortenerServer source = new ShortenerServer();
    try {
      source.init(DEFAULT_SERVER_HOST, 0);
      String adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + source.getPortAdmin();
      String redirectUrl = "http://" + DEFAULT_SERVER_HOST + ":" + source.getPortRedirect();
      TestAuth.loginAsAdmin(adminUrl);
      URLShortenerClient links = new URLShortenerClient(adminUrl, redirectUrl);
      CampaignClient cc = new CampaignClient(adminUrl);

      Campaign c = cc.create(new CampaignCreateRequest("iX 07/2026", "issue", CampaignType.MAGAZINE, null, null)).getOrThrow();
      campaignId = c.id();
      links.createCustomMapping("cm-a", "https://example.com/a", null, null, c.id(), List.of("print", "ix"), "Cover-QR");
      cc.createTag("empty-registry-tag").getOrThrow(); // an empty (count 0) tag must also round-trip

      snapshot = cc.exportMeta().getOrThrow();
      assertEquals(1, snapshot.campaigns().size());
      assertEquals(c.id(), snapshot.campaignOfLink().get("cm-a"));
    } finally {
      source.shutdown();
    }

    // ── fresh server: import, then verify all three levels restored ─────────────
    ShortenerServer target = new ShortenerServer();
    try {
      target.init(DEFAULT_SERVER_HOST, 0);
      String adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + target.getPortAdmin();
      TestAuth.loginAsAdmin(adminUrl);
      CampaignClient cc = new CampaignClient(adminUrl);

      assertTrue(cc.get(campaignId).isFailure(), "fresh server has no campaigns yet");
      assertTrue(cc.importMeta(snapshot, true).getOrThrow());

      // 1) campaign definitions
      assertEquals("iX 07/2026", cc.get(campaignId).getOrThrow().name());
      // 2) per-link membership + label
      var meta = cc.linkMetaTyped("cm-a").getOrThrow();
      assertEquals(campaignId, meta.campaignId());
      assertEquals("Cover-QR", meta.label());
      // 3) tags — incl. the empty registry tag (count 0)
      assertEquals(Map.of("print", 1, "ix", 1, "empty-registry-tag", 0), counts(cc));
    } finally {
      target.shutdown();
      TestAuth.clear();
    }
  }

  @Test
  void mergeImportKeepsBothCampaignsAndTags() throws Exception {
    TestAuth.useSeededInMemoryDirectory();

    // ── server A: campaign A + tag alpha, then export ──────────────────────────
    String campA;
    CampaignMetaExport snapshotA;
    ShortenerServer a = new ShortenerServer();
    try {
      a.init(DEFAULT_SERVER_HOST, 0);
      String adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + a.getPortAdmin();
      String redirectUrl = "http://" + DEFAULT_SERVER_HOST + ":" + a.getPortRedirect();
      TestAuth.loginAsAdmin(adminUrl);
      URLShortenerClient links = new URLShortenerClient(adminUrl, redirectUrl);
      CampaignClient cc = new CampaignClient(adminUrl);
      campA = cc.create(new CampaignCreateRequest("A-camp", null, CampaignType.OTHER, null, null)).getOrThrow().id();
      links.createCustomMapping("m-a", "https://example.com/a", null, null, null, List.of("alpha"), null);
      snapshotA = cc.exportMeta().getOrThrow();
    } finally {
      a.shutdown();
    }

    // ── server B: campaign B + tag beta, then MERGE-import A (replace=false) ─────
    ShortenerServer b = new ShortenerServer();
    try {
      b.init(DEFAULT_SERVER_HOST, 0);
      String adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + b.getPortAdmin();
      String redirectUrl = "http://" + DEFAULT_SERVER_HOST + ":" + b.getPortRedirect();
      TestAuth.loginAsAdmin(adminUrl);
      URLShortenerClient links = new URLShortenerClient(adminUrl, redirectUrl);
      CampaignClient cc = new CampaignClient(adminUrl);
      String campB = cc.create(new CampaignCreateRequest("B-camp", null, CampaignType.OTHER, null, null)).getOrThrow().id();
      links.createCustomMapping("m-b", "https://example.com/b", null, null, null, List.of("beta"), null);

      assertTrue(cc.importMeta(snapshotA, false).getOrThrow()); // merge, not replace

      Set<String> ids = cc.list().getOrThrow().stream().map(Campaign::id).collect(Collectors.toSet());
      assertTrue(ids.contains(campA) && ids.contains(campB), "merge keeps both campaigns: " + ids);
      assertEquals(Map.of("alpha", 1, "beta", 1), counts(cc), "tags of both are merged");
    } finally {
      b.shutdown();
      TestAuth.clear();
    }
  }

  private static Map<String, Integer> counts(CampaignClient cc) {
    List<TagInfo> tags = cc.listTags().getOrThrow();
    return tags.stream().collect(Collectors.toMap(TagInfo::tag, TagInfo::linkCount, Integer::sum));
  }
}
