package junit.com.svenruppert.urlshortener.api.store.campaign;

import com.svenruppert.urlshortener.api.store.campaign.CampaignStore;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryCampaignStore;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignStoreTest {

  private final CampaignStore store = new InMemoryCampaignStore();

  private Campaign campaign(String name) {
    return store.create(name, "desc", CampaignType.MAGAZINE, null, null, 42L);
  }

  @Test
  void oneToMany_reAssignReplaces() {
    Campaign a = campaign("A");
    Campaign b = campaign("B");
    store.assignLink("x", a.id());
    assertEquals(Set.of("x"), store.linksOfCampaign(a.id()));

    store.assignLink("x", b.id()); // 1:many — moves to B
    assertEquals(b.id(), store.campaignIdOfLink("x").orElseThrow());
    assertTrue(store.linksOfCampaign(a.id()).isEmpty(), "must leave A");
    assertEquals(Set.of("x"), store.linksOfCampaign(b.id()));
  }

  @Test
  void tags_normalizedAndReverseLookup() {
    store.setTags("x", Set.of("  IX  ", "Print", "ix"));
    assertEquals(Set.of("ix", "print"), store.tagsOfLink("x"));
    assertEquals(Set.of("x"), store.linksOfTag("IX")); // query normalized too
    store.setTags("y", Set.of("print"));
    assertEquals(Set.of("x", "y"), store.linksOfTag("print"));
  }

  @Test
  void label_setAndClear() {
    store.setLabel("x", "  Cover-QR  ");
    assertEquals("Cover-QR", store.labelOfLink("x").orElseThrow());
    store.setLabel("x", "  ");
    assertTrue(store.labelOfLink("x").isEmpty());
  }

  @Test
  void deleteCampaign_unassignsLinks() {
    Campaign a = campaign("A");
    store.assignLink("x", a.id());
    assertTrue(store.delete(a.id()));
    assertTrue(store.get(a.id()).isEmpty());
    assertTrue(store.campaignIdOfLink("x").isEmpty(), "link kept but unassigned");
  }

  @Test
  void onLinkDeleted_cascades() {
    Campaign a = campaign("A");
    store.assignLink("x", a.id());
    store.setTags("x", Set.of("t"));
    store.setLabel("x", "L");

    store.onLinkDeleted("x");

    assertTrue(store.campaignIdOfLink("x").isEmpty());
    assertTrue(store.tagsOfLink("x").isEmpty());
    assertTrue(store.labelOfLink("x").isEmpty());
    assertFalse(store.listAll().isEmpty(), "campaign itself survives");
  }
}
