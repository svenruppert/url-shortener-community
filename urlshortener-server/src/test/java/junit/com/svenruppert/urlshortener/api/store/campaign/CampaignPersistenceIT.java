package junit.com.svenruppert.urlshortener.api.store.campaign;

import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.campaign.CampaignStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.EclipseStore;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks proof that campaigns, memberships, tags and labels survive a storage
 * restart (close + reopen the same directory), via the EclipseStore-backed store.
 */
class CampaignPersistenceIT {

  private static EclipseStore open(Path dir) {
    return new EclipseStore(dir.toString(), new ShortCodeGenerator(1L), Clock.systemUTC());
  }

  @Test
  void campaignGraphSurvivesRestart(@TempDir Path dir) {
    String campaignId;
    try (EclipseStore store = open(dir)) {
      CampaignStore cs = store.getCampaignStore();
      Campaign c = cs.create("iX 07/2026", "issue", CampaignType.MAGAZINE, null, null, 7L);
      campaignId = c.id();
      cs.assignLink("cover", campaignId);
      cs.assignLink("page12", campaignId);
      cs.setLabel("cover", "Cover-QR");
      cs.setTags("cover", Set.of("Print", "ix"));
    }

    try (EclipseStore store = open(dir)) {
      CampaignStore cs = store.getCampaignStore();
      Campaign c = cs.get(campaignId).orElseThrow();
      assertEquals("iX 07/2026", c.name());
      assertEquals(CampaignType.MAGAZINE, c.type());
      assertEquals(Set.of("cover", "page12"), cs.linksOfCampaign(campaignId));
      assertEquals("Cover-QR", cs.labelOfLink("cover").orElseThrow());
      assertEquals(Set.of("ix", "print"), cs.tagsOfLink("cover"));
      assertEquals(Set.of("cover"), cs.linksOfTag("ix"));

      // cascade on link delete persists
      cs.onLinkDeleted("cover");
    }

    try (EclipseStore store = open(dir)) {
      CampaignStore cs = store.getCampaignStore();
      assertTrue(cs.campaignIdOfLink("cover").isEmpty(), "cascade must persist");
      assertTrue(cs.tagsOfLink("cover").isEmpty());
      assertEquals(Set.of("page12"), cs.linksOfCampaign(campaignId));
    }
  }
}
