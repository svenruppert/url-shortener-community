package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the extended list functionality of the URLShortenerClient.
 * Runs against a live ShortenerServer instance (JDK HttpServer).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class URLShortenerClientListTest
    implements HasLogger {

  private ShortenerServer server;
  private URLShortenerClient client;

  @BeforeEach
  void setUp()
      throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init();

    // Obtain dynamic admin/redirect URLs from the server
    String adminBase = "http://localhost:" + server.getPortAdmin();
    String redirectBase = "http://localhost:" + server.getPortRedirect();

    // Initialise client with local server endpoints
    client = new URLShortenerClient(adminBase, redirectBase);
    TestAuth.loginAsAdmin(adminBase);

    logger().info("Test server started: admin={}, redirect={}", adminBase, redirectBase);
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.shutdown();
      logger().info("Test server stopped.");
    }
  }

  @Test
  @Order(1)
  void list_all_and_filtered_by_code_and_url_and_date()
      throws Exception {
    // --- Arrange: create three mappings (two custom aliases, one automatic) ---
    var t0 = Instant.now();

    ShortUrlMapping m1 = client.createCustomMapping("ex-alpha", "https://example.com/docs");
    // slight delay to ensure distinct createdAt timestamps
    Thread.sleep(10);
    ShortUrlMapping m2 = client.createCustomMapping("ex-beta", "https://example.org/blog");
    Thread.sleep(10);
    ShortUrlMapping m3 = client.createMapping("https://docs.example.com/page");

    var t1 = Instant.now();

    // --- Act/Assert: /list/all (legacy endpoint) ---
    List<ShortUrlMapping> all = client.listAll();
    assertTrue(all.size() >= 3, "At least three mappings should exist.");

    // --- Act/Assert: /list with code fragment 'ex-' (should match ex-alpha and ex-beta) ---
    var reqCode = UrlMappingListRequest.builder()
        .codePart("ex-")
        .build();

    List<ShortUrlMapping> byCode = client.list(reqCode);
    var codes = byCode.stream().map(ShortUrlMapping::shortCode).collect(Collectors.toSet());
    assertTrue(codes.contains("ex-alpha"), "Result must contain ex-alpha.");
    assertTrue(codes.contains("ex-beta"), "Result must contain ex-beta.");

    // --- Act/Assert: /list with URL fragment 'docs' (should match both example.com/docs and docs.example.com/page) ---
    var reqUrl = UrlMappingListRequest.builder()
        .urlPart("docs")
        .build();
    List<ShortUrlMapping> byUrl = client.list(reqUrl);
    var urls = byUrl.stream().map(ShortUrlMapping::originalUrl).collect(Collectors.toList());
    assertTrue(urls.stream().anyMatch(u -> u.contains("example.com/docs")), "Expected URL containing /docs.");
    assertTrue(urls.stream().anyMatch(u -> u.contains("docs.example.com")), "Expected URL containing docs.example.com.");

    // --- Act/Assert: /list with inclusive date range [t0, t1] ---
    var reqDate = UrlMappingListRequest.builder()
        .from(t0)
        .to(t1)
        .build();
    List<ShortUrlMapping> byDate = client.list(reqDate);
    // All newly created entries should fall within [t0, t1]:
    var createdAllInside = byDate.stream()
        .map(ShortUrlMapping::shortCode)
        .filter(code -> code.equals(m1.shortCode()) || code.equals(m2.shortCode()) || code.equals(m3.shortCode()))
        .count();
    assertTrue(createdAllInside >= 3, "Newly created mappings must be found within the given time range.");

    // --- Raw JSON check for /list (smoke test only) ---
    String raw = client.listAsJson(reqCode);
    assertNotNull(raw);
    assertTrue(raw.contains("\"items\""), "JSON response must contain an items array.");
  }

  @Test
  @Order(2)
  void list_pagination_and_sorting()
      throws Exception {
    // --- Arrange: add several entries (in case previous tests created few) ---
    for (int i = 0; i < 10; i++) {
      client.createMapping("https://example.net/page-" + i);
      Thread.sleep(2);
    }

    // --- Act: request page 2, size 5, sorted by createdAt descending ---
    var req = UrlMappingListRequest.builder()
        .page(2).size(5)
        .sort("createdAt").dir("desc")
        .build();

    List<ShortUrlMapping> page2 = client.list(req);
    logger().info("page2 .. {}", page2);

    // --- Assert: exactly five items on the second page (if dataset large enough) ---
    assertEquals(5, page2.size(),
                 "Expected five elements on page 2 for size=5, provided sufficient data exists.");

    // Verify that createdAt is not increasing (descending order)
    for (int i = 1; i < page2.size(); i++) {
      var prev = page2.get(i - 1).createdAt();
      var cur = page2.get(i).createdAt();
      assertTrue(!cur.isAfter(prev),
                 "Expected descending order by createdAt: current (" + cur + ") must not be after previous (" + prev + ").");
    }
  }

  @Test
  @Order(3)
  void legacy_endpoints_smoke()
      throws Exception {
    // Smoke tests only: ensure legacy endpoints return valid JSON containing items[]
    String jsonAll = client.listAllAsJson();
    assertNotNull(jsonAll);
    assertTrue(jsonAll.contains("\"items\""), "/list/all must contain an items array.");

    String jsonActive = client.listActiveAsJson();
    assertNotNull(jsonActive);
    assertTrue(jsonActive.contains("\"items\""), "/list/active must contain an items array.");

    String jsonExpired = client.listExpiredAsJson();
    assertNotNull(jsonExpired);
    assertTrue(jsonExpired.contains("\"items\""), "/list/expired must contain an items array.");

    // Note: The in-memory store typically leaves expiresAt empty → often zero matches.
    // We therefore only validate structure, not content.
  }
}
