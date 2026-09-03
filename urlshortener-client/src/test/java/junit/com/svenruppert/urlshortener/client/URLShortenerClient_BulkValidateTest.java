package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.BulkValidateResponse;
import com.svenruppert.urlshortener.core.urlmapping.BulkValidateResponse.ValidationItemResult;
import com.svenruppert.urlshortener.core.urlmapping.BulkValidateResponse.ValidationStatus;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link URLShortenerClient#bulkValidate}.
 * An embedded {@link ShortenerServer} is started on a dynamic port for each test class run.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class URLShortenerClient_BulkValidateTest {

  private static ShortenerServer server;
  private static URLShortenerClient client;

  @BeforeAll
  static void startServer() throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init("localhost", 0);
    final int adminPort = server.getPortAdmin();
    final int redirectPort = server.getPortRedirect();
    final String adminUrl = "http://localhost:" + adminPort;
    final String redirectUrl = "http://localhost:" + redirectPort;
    client = new URLShortenerClient(adminUrl, redirectUrl);
    TestAuth.loginAsAdmin(adminUrl);
  }

  @AfterAll
  static void stopServer() {
    if (server != null) {
      server.shutdown();
    }
  }

  // ── Happy path ───────────────────────────────────────────────────────────────

  @Test
  @Order(1)
  void allValidUrls_returnValidStatus() throws IOException {
    final var response = client.bulkValidate(
        List.of("https://example.com/page-a", "https://example.org/page-b"),
        List.of());
    assertThat(response.getResults()).hasSize(2);
    assertThat(response.getResults()).allMatch(r -> r.getStatus() == ValidationStatus.VALID);
  }

  @Test
  @Order(2)
  void singleUrl_returnsOneResult() throws IOException {
    final var response = client.bulkValidate(
        List.of("https://openai.com/docs"), List.of());
    assertThat(response.getResults()).hasSize(1);
    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.VALID);
  }

  @Test
  @Order(3)
  void indexField_matchesInputOrder() throws IOException {
    final var response = client.bulkValidate(
        List.of("https://example.com/a", "https://example.com/b", "https://example.com/c"),
        List.of());
    final var results = response.getResults();
    assertThat(results.get(0).getIndex()).isEqualTo(0);
    assertThat(results.get(1).getIndex()).isEqualTo(1);
    assertThat(results.get(2).getIndex()).isEqualTo(2);
  }

  @Test
  @Order(4)
  void originalUrl_isPreserved() throws IOException {
    final String url = "https://preserved-url.example.com/path";
    final var response = client.bulkValidate(List.of(url), List.of());
    assertThat(response.getResults().get(0).getOriginalUrl()).isEqualTo(url);
  }

  @Test
  @Order(5)
  void normalizedUrl_isReturned() throws IOException {
    final String url = "https://example.com/normalized";
    final var response = client.bulkValidate(List.of(url), List.of());
    assertThat(response.getResults().get(0).getNormalizedUrl()).isNotBlank();
  }

  // ── Blocking statuses ────────────────────────────────────────────────────────

  @Test
  @Order(10)
  void blankUrl_returnsEmptyStatus() throws IOException {
    final var response = client.bulkValidate(List.of("   "), List.of());
    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.EMPTY);
    assertThat(response.getResults().get(0).isBlocking()).isTrue();
  }

  @Test
  @Order(11)
  void invalidUrl_noScheme_returnsInvalidUrlStatus() throws IOException {
    final var response = client.bulkValidate(List.of("not-a-url-at-all"), List.of());
    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.INVALID_URL);
    assertThat(response.getResults().get(0).isBlocking()).isTrue();
    assertThat(response.getResults().get(0).getErrorMessage()).isNotBlank();
  }

  @Test
  @Order(12)
  void invalidUrl_forbiddenScheme_returnsInvalidUrlStatus() throws IOException {
    final var response = client.bulkValidate(List.of("ftp://forbidden.example.com"), List.of());
    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.INVALID_URL);
  }

  @Test
  @Order(13)
  void tooLongUrl_returnsTooLongStatus() throws IOException {
    final String longUrl = "https://example.com/" + "x".repeat(2100);
    final var response = client.bulkValidate(List.of(longUrl), List.of());
    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.TOO_LONG);
    assertThat(response.getResults().get(0).isBlocking()).isTrue();
  }

  @Test
  @Order(14)
  void duplicateInBatch_secondEntryIsMarked() throws IOException {
    final String url = "https://example.com/duplicate-batch";
    final var response = client.bulkValidate(List.of(url, url), List.of());
    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.VALID);
    assertThat(response.getResults().get(1).getStatus()).isEqualTo(ValidationStatus.DUPLICATE_IN_BATCH);
    assertThat(response.getResults().get(1).isBlocking()).isTrue();
  }

  @Test
  @Order(15)
  void duplicateInGrid_returnsGridDuplicateStatus() throws IOException {
    final String url = "https://example.com/in-grid-already";
    final var response = client.bulkValidate(
        List.of(url),
        List.of(url));  // url already in grid
    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.DUPLICATE_IN_GRID);
    assertThat(response.getResults().get(0).isBlocking()).isTrue();
  }

  // ── Mixed batch ──────────────────────────────────────────────────────────────

  @Test
  @Order(20)
  void mixedBatch_correctStatusPerEntry() throws IOException {
    final var response = client.bulkValidate(
        List.of(
            "https://valid-url.example.com/page",   // VALID
            "not-valid",                              // INVALID_URL
            "https://valid-url.example.com/page"    // DUPLICATE_IN_BATCH
        ),
        List.of());
    final var results = response.getResults();
    assertThat(results.get(0).getStatus()).isEqualTo(ValidationStatus.VALID);
    assertThat(results.get(1).getStatus()).isEqualTo(ValidationStatus.INVALID_URL);
    assertThat(results.get(2).getStatus()).isEqualTo(ValidationStatus.DUPLICATE_IN_BATCH);
  }

  @Test
  @Order(21)
  void existingShortCodes_emptyForFreshUrl() throws IOException {
    final var response = client.bulkValidate(
        List.of("https://fresh-url-never-seen.example.com/xyz123"), List.of());
    final ValidationItemResult result = response.getResults().get(0);
    assertThat(result.getStatus()).isEqualTo(ValidationStatus.VALID);
    assertThat(result.getExistingShortCodes()).isEmpty();
  }

  @Test
  @Order(22)
  void existingShortlinks_detectedAfterCreation() throws IOException {
    // First create a shortlink for a URL
    final String targetUrl = "https://existing-shortlink-test.example.com/page";
    client.bulkShorten(List.of(targetUrl));

    // Now validate the same URL — should return HAS_EXISTING_SHORTLINKS
    final var response = client.bulkValidate(List.of(targetUrl), List.of());
    final ValidationItemResult result = response.getResults().get(0);
    assertThat(result.getStatus()).isEqualTo(ValidationStatus.HAS_EXISTING_SHORTLINKS);
    assertThat(result.isCreatable()).isTrue();   // non-blocking warning
    assertThat(result.isBlocking()).isFalse();
    assertThat(result.getExistingShortCodes()).isNotEmpty();
  }

  @Test
  @Order(23)
  void hasExistingShortlinks_isCreatable() throws IOException {
    // Create a shortlink first
    final String targetUrl = "https://creatable-despite-existing.example.com/abc";
    client.bulkShorten(List.of(targetUrl));

    // Validate — should be creatable (non-blocking warning)
    final var response = client.bulkValidate(List.of(targetUrl), List.of());
    assertThat(response.getResults().get(0).isCreatable()).isTrue();
  }

  // ── Limits ───────────────────────────────────────────────────────────────────

  @Test
  @Order(30)
  void nullUrls_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> client.bulkValidate(null, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Order(31)
  void emptyUrls_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> client.bulkValidate(List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Order(32)
  void largeBatch_processedCorrectly() throws IOException {
    final var urls = IntStream.range(0, 20)
        .mapToObj(i -> "https://bulk-validate-batch.example.com/page-" + i)
        .toList();
    final var response = client.bulkValidate(urls, List.of());
    assertThat(response.getResults()).hasSize(20);
    assertThat(response.getResults()).allMatch(r -> r.getStatus() == ValidationStatus.VALID);
  }

  // ── existingUrls parameter ───────────────────────────────────────────────────

  @Test
  @Order(40)
  void nullExistingUrls_treatedAsEmpty() throws IOException {
    final var response = client.bulkValidate(
        List.of("https://null-existing.example.com/page"), null);
    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.VALID);
  }

  @Test
  @Order(41)
  void multipleExistingUrls_onlyNewSubmittedUrlMarkedAsDuplicate() throws IOException {
    final String existingA = "https://grid-existing-a.example.com/";
    final String existingB = "https://grid-existing-b.example.com/";
    final String newUrl    = "https://grid-new.example.com/";

    final var response = client.bulkValidate(
        List.of(newUrl, existingA),
        List.of(existingA, existingB));

    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.VALID);
    assertThat(response.getResults().get(1).getStatus()).isEqualTo(ValidationStatus.DUPLICATE_IN_GRID);
  }

  // ── Existing shortlink detail (item 2) ───────────────────────────────────────

  @Test
  @Order(50)
  void existingShortCodes_containActualShortCodeStrings() throws IOException {
    // Create a shortlink for a target URL
    final String targetUrl = "https://shortcode-detail-test.example.com/p1";
    final var createResponse = client.bulkShorten(List.of(targetUrl));
    final String createdCode = createResponse.getResults().get(0).getShortCode();
    assertThat(createdCode).isNotBlank();

    // Validate the same URL — existingShortCodes must contain the created shortcode
    final var validateResponse = client.bulkValidate(List.of(targetUrl), List.of());
    final ValidationItemResult result = validateResponse.getResults().get(0);
    assertThat(result.getStatus()).isEqualTo(ValidationStatus.HAS_EXISTING_SHORTLINKS);
    assertThat(result.getExistingShortCodes()).contains(createdCode);
  }

  @Test
  @Order(51)
  void existingShortCodes_multipleLinks_allReturned() throws IOException {
    // Create two shortlinks for the same target URL
    final String targetUrl = "https://multi-shortcode-detail.example.com/p2";
    final var r1 = client.bulkShorten(List.of(targetUrl));
    final var r2 = client.bulkShorten(List.of(targetUrl));
    final String code1 = r1.getResults().get(0).getShortCode();
    final String code2 = r2.getResults().get(0).getShortCode();

    // Both shortcodes must appear in the validation response
    final var validateResponse = client.bulkValidate(List.of(targetUrl), List.of());
    final var codes = validateResponse.getResults().get(0).getExistingShortCodes();
    assertThat(codes).contains(code1, code2);
  }

  // ── Shortcode format (item 6) ────────────────────────────────────────────────

  @Test
  @Order(60)
  void createdShortCode_isExactly6CharactersLong() throws IOException {
    final var response = client.bulkShorten(
        List.of("https://shortcode-length-test.example.com/x"));
    final String code = response.getResults().get(0).getShortCode();
    assertThat(code).hasSize(6);
  }

  @Test
  @Order(61)
  void createdShortCodes_areBase62Alphanumeric() throws IOException {
    final var urls = IntStream.range(0, 5)
        .mapToObj(i -> "https://shortcode-alphanum.example.com/item-" + i)
        .toList();
    final var response = client.bulkShorten(urls);
    for (final var item : response.getResults()) {
      assertThat(item.getShortCode())
          .as("Short code must be exactly 6 Base-62 characters, got: " + item.getShortCode())
          .matches("[0-9A-Za-z]{6}");
    }
  }

  @Test
  @Order(62)
  void consecutiveShortCodes_areNotSequentiallyIncremented() throws IOException {
    // Consecutive counter values produce very different codes after mixing
    final var r1 = client.bulkShorten(List.of("https://seq-check-1.example.com/"));
    final var r2 = client.bulkShorten(List.of("https://seq-check-2.example.com/"));
    final String code1 = r1.getResults().get(0).getShortCode();
    final String code2 = r2.getResults().get(0).getShortCode();
    assertThat(code1).isNotEqualTo(code2);
    // Codes must not be trivially sequential numeric strings.
    // If either code is non-numeric (contains letters) that already proves non-sequential.
    // Only when both happen to be all-digit strings do we check they are not adjacent.
    try {
      final long n1 = Long.parseLong(code1);
      final long n2 = Long.parseLong(code2);
      assertThat(Math.abs(n1 - n2)).isGreaterThan(1L);
    } catch (final NumberFormatException e) {
      // Non-numeric code – definitely not a trivially sequential integer; test passes.
    }
  }

  @Test
  @Order(63)
  void existingShortCodes_inValidateResponse_areAlso6CharsLong() throws IOException {
    final String targetUrl = "https://shortcode-6char-validate.example.com/v";
    client.bulkShorten(List.of(targetUrl)); // create shortlink

    final var response = client.bulkValidate(List.of(targetUrl), List.of());
    final var codes = response.getResults().get(0).getExistingShortCodes();
    assertThat(codes).isNotEmpty();
    for (final String code : codes) {
      assertThat(code).hasSize(6);
    }
  }

  // ── Re-validation against full work set (item 1) ────────────────────────────

  @Test
  @Order(70)
  void revalidate_withBlockingItemAsExisting_detectsDuplicate() throws IOException {
    // Simulate the UI passing a blocking item's URL in existingUrls during re-validation
    // (blocking items are also part of the work set and must be included)
    final String existingBlockingUrl = "not-valid-but-in-grid";   // blocking item
    final String newUrl = "https://revalidate-dup-check.example.com/x";

    // Validate a new URL that duplicates the blocking item's URL
    // (in practice this scenario tests that the server respects existingUrls regardless of state)
    final var response = client.bulkValidate(
        List.of(newUrl, newUrl),           // second entry duplicates first within batch
        List.of(existingBlockingUrl));      // existingUrls includes the blocking item's URL

    // First entry: valid (no duplicate vs. batch or grid)
    assertThat(response.getResults().get(0).getStatus()).isEqualTo(ValidationStatus.VALID);
    // Second entry: duplicate within batch
    assertThat(response.getResults().get(1).getStatus()).isEqualTo(ValidationStatus.DUPLICATE_IN_BATCH);
  }

  @Test
  @Order(71)
  void revalidate_urlAlreadyInGridAsCreated_detectedAsDuplicateInGrid() throws IOException {
    // After Create, items in CREATED state should still count as "existing"
    // when re-validating a newly added URL in the same work set
    final String targetUrl = "https://created-item-duplicate.example.com/c";
    client.bulkShorten(List.of(targetUrl)); // create it via the server

    // Simulating UI behaviour: validate a new URL that duplicates an already-CREATED grid item
    // The UI passes CREATED items' URLs in existingUrls during re-validation
    final var response = client.bulkValidate(
        List.of(targetUrl),          // URL already "in grid" as CREATED
        List.of(targetUrl));         // pass it as existing

    assertThat(response.getResults().get(0).getStatus())
        .isEqualTo(ValidationStatus.DUPLICATE_IN_GRID);
  }
}
