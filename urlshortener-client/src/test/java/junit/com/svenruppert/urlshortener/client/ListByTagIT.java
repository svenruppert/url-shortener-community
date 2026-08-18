package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.AuthContext;
import com.svenruppert.urlshortener.client.CampaignClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks IT for the tag filter on the link listing (P010): create links, tag a
 * subset, then list/count with {@code tag=} against the real in-process server and
 * assert only the tagged links come back (and count matches). Tags are normalized
 * (lower/trim) server-side, so a mixed-case filter still matches.
 */
class ListByTagIT {

  private ShortenerServer server;
  private String adminUrl;
  private URLShortenerClient client;
  private CampaignClient cc;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void start() throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    String redirectUrl = "http://" + DEFAULT_SERVER_HOST + ":" + server.getPortRedirect();
    TestAuth.loginAsAdmin(adminUrl); // installs the bearer token as the SDK default
    client = new URLShortenerClient(adminUrl, redirectUrl);
    cc = new CampaignClient(adminUrl);
  }

  @AfterEach
  void stop() {
    server.shutdown();
    TestAuth.clear();
  }

  @Test
  void listFiltersByTag() throws Exception {
    shorten("tag-a", "https://example.com/a");
    shorten("tag-b", "https://example.com/b");
    shorten("tag-c", "https://example.com/c");

    // two links carry "print", one carries only "web"
    assertTrue(cc.setTags("tag-a", List.of("Print", "ix")).getOrThrow());
    assertTrue(cc.setTags("tag-b", List.of("print")).getOrThrow());
    assertTrue(cc.setTags("tag-c", List.of("web")).getOrThrow());

    // mixed-case filter still matches (normalized server-side)
    var printReq = UrlMappingListRequest.builder().tag("PRINT").build();
    List<ShortUrlMapping> print = client.list(printReq);
    assertEquals(Set.of("tag-a", "tag-b"),
        print.stream().map(ShortUrlMapping::shortCode).collect(java.util.stream.Collectors.toSet()));
    assertEquals(2, client.listCount(printReq), "count must match the filtered result");

    // a tag on a single link
    var webReq = UrlMappingListRequest.builder().tag("web").build();
    assertEquals(List.of("tag-c"),
        client.list(webReq).stream().map(ShortUrlMapping::shortCode).toList());
    assertEquals(1, client.listCount(webReq));

    // unknown tag -> empty, not "all"
    var unknownReq = UrlMappingListRequest.builder().tag("does-not-exist").build();
    assertTrue(client.list(unknownReq).isEmpty(), "unknown tag yields no links");
    assertEquals(0, client.listCount(unknownReq));

    // no tag filter -> all three links
    assertEquals(3, client.listCount(UrlMappingListRequest.builder().build()));
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
}
