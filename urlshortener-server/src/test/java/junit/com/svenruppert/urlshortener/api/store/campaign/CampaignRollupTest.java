package junit.com.svenruppert.urlshortener.api.store.campaign;

import com.svenruppert.urlshortener.api.store.campaign.CampaignStatisticsService;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryCampaignStore;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryStatisticsStore;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignStatisticsResponse;
import com.svenruppert.urlshortener.core.campaign.CampaignType;
import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * No-mocks rollup: real InMemory statistics + campaign store. Verifies the
 * campaign total/timeline exclude bots and the per-link ranking carries the
 * placement label and share.
 */
class CampaignRollupTest {

  private static final Instant TS = LocalDateTime.of(2026, 7, 24, 10, 0).toInstant(ZoneOffset.UTC);
  private static final LocalDate DAY = LocalDate.of(2026, 7, 24);

  private final InMemoryStatisticsStore stats = new InMemoryStatisticsStore(Clock.fixed(TS, ZoneOffset.UTC));
  private final InMemoryCampaignStore campaigns = new InMemoryCampaignStore();
  private final CampaignStatisticsService svc = new CampaignStatisticsService(campaigns, stats);

  private void human(String code) {
    stats.recordEvent(new RedirectEvent(code, TS, "Mozilla/5.0 Chrome/124", null, null, null));
  }

  private void bot(String code) {
    stats.recordEvent(new RedirectEvent(code, TS, "TelegramBot", null, null, null));
  }

  @Test
  void campaignRollup_humanOnly_withPlacementShares() {
    Campaign c = campaigns.create("iX 07/2026", null, CampaignType.MAGAZINE, null, null, 1L);
    campaigns.assignLink("cover", c.id());
    campaigns.assignLink("page12", c.id());
    campaigns.setLabel("cover", "Cover-QR");
    campaigns.setLabel("page12", "Page 12");

    human("cover");
    human("cover");
    human("cover");
    bot("cover");     // preview bot — excluded
    human("page12");

    CampaignStatisticsResponse r = svc.forCampaign(c.id(), DAY, DAY);

    assertEquals(4, r.total(), "3 + 1 human hits (bot excluded)");
    assertEquals(1, r.timeline().size());
    assertEquals(4, r.timeline().getFirst().count());

    // ranked by count desc: cover (3, 75%) then page12 (1, 25%)
    var top = r.perLink().getFirst();
    assertEquals("cover", top.shortCode());
    assertEquals("Cover-QR", top.label());
    assertEquals(3, top.count());
    assertEquals(0.75, top.share(), 1e-9);
    assertEquals("page12", r.perLink().get(1).shortCode());
    assertEquals(0.25, r.perLink().get(1).share(), 1e-9);
  }

  @Test
  void tagRollup_aggregatesAcrossTaggedLinks() {
    campaigns.setTags("a", Set.of("Exodos"));
    campaigns.setTags("b", Set.of("exodos"));
    human("a");
    human("b");
    human("b");
    bot("b");

    CampaignStatisticsResponse r = svc.forTag("EXODOS", DAY, DAY);
    assertEquals(3, r.total(), "1 + 2 human across the two tagged links");
    assertEquals(2, r.perLink().size());
  }
}
