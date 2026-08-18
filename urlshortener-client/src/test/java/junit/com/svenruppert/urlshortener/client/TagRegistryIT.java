package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.CampaignClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.campaign.TagInfo;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks IT for first-class tags (create empty tags, usage counts incl. 0, and
 * replace-by-1..n) against the real in-process server.
 */
class TagRegistryIT {

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
  void createEmpty_count_replace_delete() throws Exception {
    // create an empty tag — appears with count 0
    assertTrue(cc.createTag("Draft").getOrThrow());
    assertEquals(Map.of("draft", 0), counts());

    // assigning a tag to a link auto-registers it and counts it
    client.createCustomMapping("tr-1", "https://example.com/1", null, null, null, List.of("print"), null);
    assertEquals(Map.of("draft", 0, "print", 1), counts());

    // replace print by two tags across all links — print is retired
    assertTrue(cc.replaceTag("print", List.of("news", "web")).getOrThrow());
    assertEquals(Map.of("draft", 0, "news", 1, "web", 1), counts());

    // rename is the 1->1 special case
    assertTrue(cc.renameTag("news", "press").getOrThrow());
    assertEquals(Map.of("draft", 0, "press", 1, "web", 1), counts());

    // deleting the empty tag removes it from the registry
    assertTrue(cc.deleteTag("draft").getOrThrow());
    assertEquals(Map.of("press", 1, "web", 1), counts());
  }

  @Test
  void assignTagAddsToExistingTags() throws Exception {
    cc.createTag("promo").getOrThrow();
    client.createCustomMapping("at-1", "https://example.com/1", null, null);
    client.createCustomMapping("at-2", "https://example.com/2", null, null, null, List.of("keep"), null);

    assertTrue(cc.assignTagToLinks("promo", List.of("at-1", "at-2")).getOrThrow());

    assertEquals(2, client.listCount(UrlMappingListRequest.builder().tag("promo").build()), "both links carry promo");
    // union, not replace: the pre-existing tag stays
    var meta = cc.linkMetaTyped("at-2").getOrThrow();
    assertEquals(java.util.Set.of("keep", "promo"), new java.util.HashSet<>(meta.tags()));
  }

  private Map<String, Integer> counts() {
    List<TagInfo> tags = cc.listTags().getOrThrow();
    return tags.stream().collect(Collectors.toMap(TagInfo::tag, TagInfo::linkCount, Integer::sum));
  }
}
