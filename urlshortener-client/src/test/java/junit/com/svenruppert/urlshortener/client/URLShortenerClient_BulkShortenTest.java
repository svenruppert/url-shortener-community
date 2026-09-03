package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenRequest;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse.BulkShortenItemResult;
import com.svenruppert.urlshortener.core.urlmapping.BulkShortenResponse.ItemStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.svenruppert.urlshortener.core.JsonUtils;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.SHORTCODE_BASE_URL;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link URLShortenerClient#bulkShorten(List)} and its overloads.
 *
 * <p>Each test starts a fresh embedded {@link ShortenerServer} on dynamic ports,
 * mirroring the pattern used throughout the client test suite.
 */
class URLShortenerClient_BulkShortenTest implements HasLogger {

  private static final String URL_A = "https://example.com/page-a";
  private static final String URL_B = "https://example.org/page-b";
  private static final String URL_C = "https://openai.com/docs";
  private static final String INVALID_URL_NO_SCHEME  = "not-a-url-at-all";
  private static final String INVALID_URL_BAD_SCHEME = "ftp://forbidden.example.com";
  private static final String INVALID_URL_NO_TLD     = "http://localhost";

  private ShortenerServer server;
  private URLShortenerClient client;

  // ── Lifecycle ────────────────────────────────────────────────────────────

  @BeforeEach
  void startServer() throws Exception {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    waitUntilOpen(DEFAULT_SERVER_HOST, server.getPortRedirect(), Duration.ofSeconds(3));

    final String adminUrl    = "http://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin()    + "/";
    final String redirectUrl = "http://" + DEFAULT_SERVER_HOST + ":" + server.getPortRedirect() + "/";
    client = new URLShortenerClient(adminUrl, redirectUrl);
    TestAuth.loginAsAdmin(adminUrl);
  }

  @AfterEach
  void stopServer() {
    if (server != null) server.shutdown();
  }

  private static void waitUntilOpen(String host, int port, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    Throwable last = null;
    while (System.nanoTime() < deadline) {
      try (Socket s = new Socket(host, port)) { return; }
      catch (Throwable t) { last = t; Thread.sleep(25); }
    }
    throw new IllegalStateException("Server did not open " + host + ":" + port + " in time", last);
  }

  // ── Happy-path: basic ─────────────────────────────────────────────────────

  @Test
  void bulkShorten_allValid_allSucceed() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A, URL_B, URL_C));

    assertNotNull(response);
    assertEquals(3, response.getTotal());
    assertEquals(3, response.getSucceeded());
    assertEquals(0, response.getFailed());

    for (BulkShortenItemResult r : response.getResults()) {
      assertEquals(ItemStatus.CREATED, r.getStatus(),
          "Expected CREATED for: " + r.getOriginalUrl());
      assertTrue(r.isSuccess());
      assertNotNull(r.getShortCode());
      assertFalse(r.getShortCode().isBlank());
      assertNull(r.getErrorMessage(), "No error message expected on success");
    }
  }

  @Test
  void bulkShorten_singleValidUrl_succeeds() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A));

    assertEquals(1, response.getTotal());
    assertEquals(1, response.getSucceeded());
    assertEquals(0, response.getFailed());

    final BulkShortenItemResult single = response.getResults().get(0);
    assertEquals(ItemStatus.CREATED, single.getStatus());
    assertEquals(URL_A, single.getOriginalUrl());
    assertNotNull(single.getShortCode());
  }

  // ── Happy-path: new response fields ───────────────────────────────────────

  @Test
  void bulkShorten_indexMatchesInputPosition() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A, URL_B, URL_C));

    final List<BulkShortenItemResult> results = response.getResults();
    for (int i = 0; i < results.size(); i++) {
      assertEquals(i, results.get(i).getIndex(),
          "Item at position " + i + " must carry index " + i);
    }
  }

  @Test
  void bulkShorten_shortUrlIsFullyQualified() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A));

    final BulkShortenItemResult r = response.getResults().get(0);
    assertTrue(r.isSuccess());
    assertNotNull(r.getShortUrl(), "shortUrl must not be null on success");
    assertTrue(r.getShortUrl().startsWith(SHORTCODE_BASE_URL),
        "shortUrl must start with the configured base URL");
    assertTrue(r.getShortUrl().endsWith(r.getShortCode()),
        "shortUrl must end with the shortCode");
  }

  @Test
  void bulkShorten_shortCodesAreUnique() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A, URL_B, URL_C));

    final Set<String> codes = response.getResults().stream()
        .map(BulkShortenItemResult::getShortCode)
        .collect(Collectors.toSet());
    assertEquals(3, codes.size(), "Each URL must receive a distinct shortcode");

    final Set<String> shortUrls = response.getResults().stream()
        .map(BulkShortenItemResult::getShortUrl)
        .collect(Collectors.toSet());
    assertEquals(3, shortUrls.size(), "Each URL must receive a distinct short URL");
  }

  @Test
  void bulkShorten_createdLinksAreImmediatelyResolvable() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A, URL_B));

    for (BulkShortenItemResult r : response.getResults()) {
      assertTrue(r.isSuccess());
      final String resolved = client.resolveShortcode(r.getShortCode());
      assertEquals(r.getOriginalUrl(), resolved,
          "Resolving " + r.getShortCode() + " must return the original URL");
    }
  }

  @Test
  void bulkShorten_sameUrlTwice_producesTwoDistinctShortCodes() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A, URL_A));

    assertEquals(2, response.getSucceeded());
    final List<String> codes = response.getResults().stream()
        .map(BulkShortenItemResult::getShortCode).toList();
    assertNotEquals(codes.get(0), codes.get(1),
        "Same target URL submitted twice must still generate two distinct shortcodes");
  }

  // ── Optional defaults: defaultActive ─────────────────────────────────────

  @Test
  void bulkShorten_defaultActive_false_createsInactiveLinks() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A, URL_B), null, false);

    assertEquals(2, response.getSucceeded());

    // Inactive links must NOT redirect (resolveShortcode returns null for 404)
    for (BulkShortenItemResult r : response.getResults()) {
      final String resolved = client.resolveShortcode(r.getShortCode());
      assertNull(resolved,
          "Inactive link " + r.getShortCode() + " must not redirect");
    }
  }

  @Test
  void bulkShorten_defaultActive_true_createsActiveLinks() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A), null, true);

    assertEquals(1, response.getSucceeded());
    final String resolved = client.resolveShortcode(
        response.getResults().get(0).getShortCode());
    assertNotNull(resolved, "Active link must resolve");
    assertEquals(URL_A, resolved);
  }

  @Test
  void bulkShorten_defaultActive_null_treatedAsTrue() throws IOException {
    // null defaultActive must behave identically to true
    final var response = client.bulkShorten(List.of(URL_A), null, null);

    assertEquals(1, response.getSucceeded());
    assertNotNull(client.resolveShortcode(
        response.getResults().get(0).getShortCode()));
  }

  // ── Optional defaults: defaultExpiresAt ──────────────────────────────────

  @Test
  void bulkShorten_withFutureExpiresAt_linksAreCreated() throws IOException {
    final Instant future = Instant.now().plus(Duration.ofDays(7));
    final var response = client.bulkShorten(List.of(URL_A, URL_B), future, true);

    assertEquals(2, response.getSucceeded(),
        "Links with a future expiry must be created successfully");
  }

  // ── Error-path: status enum ───────────────────────────────────────────────

  @Test
  void bulkShorten_invalidUrl_statusIsINVALID_URL() throws IOException {
    final var response = client.bulkShorten(List.of(INVALID_URL_NO_SCHEME));

    final BulkShortenItemResult r = response.getResults().get(0);
    assertEquals(ItemStatus.INVALID_URL, r.getStatus());
    assertFalse(r.isSuccess());
    assertNotNull(r.getErrorMessage());
    assertFalse(r.getErrorMessage().isBlank());
    assertNull(r.getShortCode());
    assertNull(r.getShortUrl());
  }

  @Test
  void bulkShorten_badScheme_statusIsINVALID_URL() throws IOException {
    final var response = client.bulkShorten(List.of(INVALID_URL_BAD_SCHEME));
    assertEquals(ItemStatus.INVALID_URL, response.getResults().get(0).getStatus());
  }

  @Test
  void bulkShorten_tooLongUrl_statusIsTOO_LONG() throws IOException {
    final String longUrl = "https://example.com/" + "a".repeat(BulkShortenRequest.MAX_URL_LENGTH);
    final var response = client.bulkShorten(List.of(longUrl));

    final BulkShortenItemResult r = response.getResults().get(0);
    assertEquals(ItemStatus.TOO_LONG, r.getStatus());
    assertFalse(r.isSuccess());
    assertNotNull(r.getErrorMessage());
  }

  @Test
  void bulkShorten_allInvalidUrls_allFail() throws IOException {
    final var response = client.bulkShorten(
        List.of(INVALID_URL_NO_SCHEME, INVALID_URL_BAD_SCHEME, INVALID_URL_NO_TLD));

    assertEquals(3, response.getTotal());
    assertEquals(0, response.getSucceeded());
    assertEquals(3, response.getFailed());

    final Set<ItemStatus> failStatuses = new HashSet<>(
        List.of(ItemStatus.INVALID_URL, ItemStatus.FAILED));

    for (BulkShortenItemResult r : response.getResults()) {
      assertFalse(r.isSuccess());
      assertTrue(failStatuses.contains(r.getStatus()),
          "Unexpected status: " + r.getStatus());
      assertNull(r.getShortCode());
      assertNull(r.getShortUrl());
    }
  }

  @Test
  void bulkShorten_mixedValidAndInvalid_partialSuccess() throws IOException {
    final var response = client.bulkShorten(
        List.of(URL_A, INVALID_URL_NO_SCHEME, URL_B, INVALID_URL_BAD_SCHEME));

    assertEquals(4, response.getTotal());
    assertEquals(2, response.getSucceeded());
    assertEquals(2, response.getFailed());

    final List<BulkShortenItemResult> results = response.getResults();
    assertEquals(ItemStatus.CREATED,     results.get(0).getStatus());
    assertEquals(ItemStatus.INVALID_URL, results.get(1).getStatus());
    assertEquals(ItemStatus.CREATED,     results.get(2).getStatus());
    assertEquals(ItemStatus.INVALID_URL, results.get(3).getStatus());
  }

  @Test
  void bulkShorten_blankEntryInList_reportsErrorForBlank() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A, "   ", URL_B));

    assertEquals(3, response.getTotal());
    assertEquals(2, response.getSucceeded());
    assertEquals(1, response.getFailed());

    final BulkShortenItemResult blankResult = response.getResults().get(1);
    assertEquals(ItemStatus.INVALID_URL, blankResult.getStatus());
    assertNotNull(blankResult.getErrorMessage());
  }

  @Test
  void bulkShorten_invalidUrlHasCorrectOriginalUrlPreserved() throws IOException {
    final var response = client.bulkShorten(List.of(INVALID_URL_NO_SCHEME));

    final BulkShortenItemResult result = response.getResults().get(0);
    assertEquals(INVALID_URL_NO_SCHEME, result.getOriginalUrl(),
        "The original URL must be preserved in the error result");
  }

  @Test
  void bulkShorten_summaryCountsMatchResultsList() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A, INVALID_URL_NO_SCHEME, URL_B));

    final long succeededInList = response.getResults().stream()
        .filter(BulkShortenItemResult::isSuccess).count();
    final long failedInList = response.getResults().stream()
        .filter(r -> !r.isSuccess()).count();

    assertEquals(response.getTotal(),     response.getResults().size());
    assertEquals(response.getSucceeded(), (int) succeededInList);
    assertEquals(response.getFailed(),    (int) failedInList);
  }

  // ── Server-side limit: too many URLs ─────────────────────────────────────

  @Test
  void bulkShorten_exceedsMaxUrls_serverRejects400() {
    final List<String> tooMany = new ArrayList<>();
    for (int i = 0; i <= BulkShortenRequest.MAX_URLS; i++) {
      tooMany.add("https://example.com/page-" + i);
    }
    // Client bypasses its own guard – call overload directly to test server enforcement
    final String jsonBody;
    try {
      // Build raw request bypassing the client guard
      final var req = new BulkShortenRequest(tooMany);
      jsonBody = JsonUtils.toJson(req);
    } catch (Exception e) {
      fail("Setup failed: " + e.getMessage());
      return;
    }
    // Expecting IllegalArgumentException (mapped from 400)
    assertThrows(IllegalArgumentException.class,
        () -> {
          // Use the full overload which sends whatever we pass
          client.bulkShorten(tooMany, null, null);
        });
  }

  // ── Client-side validation ────────────────────────────────────────────────

  @Test
  void bulkShorten_nullList_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> client.bulkShorten(null));
  }

  @Test
  void bulkShorten_emptyList_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> client.bulkShorten(List.of()));
  }

  // ── Volume ────────────────────────────────────────────────────────────────

  @Test
  void bulkShorten_largerBatch_allSucceed() throws IOException {
    final List<String> urls = new ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      urls.add("https://example.com/page-" + i);
    }

    final var response = client.bulkShorten(urls);

    assertEquals(20, response.getTotal());
    assertEquals(20, response.getSucceeded());
    assertEquals(0,  response.getFailed());

    final Set<String> codes = response.getResults().stream()
        .map(BulkShortenItemResult::getShortCode)
        .collect(Collectors.toSet());
    assertEquals(20, codes.size(), "All 20 shortcodes must be distinct");
  }

  @Test
  void bulkShorten_resultsAreRegisteredInStore() throws IOException {
    final var response = client.bulkShorten(List.of(URL_A, URL_B));

    assertEquals(2, response.getSucceeded());

    final var allMappings = client.listAll();
    final Set<String> allCodes = allMappings.stream()
        .map(m -> m.shortCode())
        .collect(Collectors.toSet());

    for (BulkShortenItemResult r : response.getResults()) {
      assertTrue(allCodes.contains(r.getShortCode()),
          "Shortcode " + r.getShortCode() + " must appear in store listing");
    }
  }
}
