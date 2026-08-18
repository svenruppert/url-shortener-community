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
 * No-mocks IT for central tag management (list/rename/merge/delete) via the SDK
 * against the real in-process server: tag counts, rename, merge-on-rename and
 * delete are all verified end-to-end, cross-checked against the tag filter.
 */
class TagsManagementIT {

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
  void listRenameMergeDelete() throws Exception {
    client.createCustomMapping("tm-a", "https://example.com/a", null, null, null, List.of("Print", "ix"), null);
    client.createCustomMapping("tm-b", "https://example.com/b", null, null, null, List.of("print"), null);
    client.createCustomMapping("tm-c", "https://example.com/c", null, null, null, List.of("web"), null);

    // counts (tags normalized to lowercase)
    assertEquals(Map.of("print", 2, "ix", 1, "web", 1), counts());

    // rename print -> paper
    assertTrue(cc.renameTag("print", "paper").getOrThrow());
    assertEquals(Map.of("paper", 2, "ix", 1, "web", 1), counts());
    assertEquals(2, client.listCount(UrlMappingListRequest.builder().tag("paper").build()));
    assertEquals(0, client.listCount(UrlMappingListRequest.builder().tag("print").build()));

    // rename paper -> web merges into the existing web tag (a,b,c all carry web)
    assertTrue(cc.renameTag("paper", "web").getOrThrow());
    assertEquals(Map.of("web", 3, "ix", 1), counts());
    assertEquals(3, client.listCount(UrlMappingListRequest.builder().tag("web").build()));

    // delete ix
    assertTrue(cc.deleteTag("ix").getOrThrow());
    assertEquals(Map.of("web", 3), counts());
    assertEquals(0, client.listCount(UrlMappingListRequest.builder().tag("ix").build()));
  }

  private Map<String, Integer> counts() {
    List<TagInfo> tags = cc.listTags().getOrThrow();
    return tags.stream().collect(Collectors.toMap(TagInfo::tag, TagInfo::linkCount, Integer::sum));
  }
}
