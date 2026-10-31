package junit.com.svenruppert.urlshortener.api;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static com.svenruppert.dependencies.core.net.HttpStatus.CREATED;
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_PROTOCOL;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_LIST;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_SHORTEN;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_LOGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the server search contract the global Cmd-K quick-search palette
 * ({@code QuickSearchDialog}) depends on: a single free-text term must find
 * links whose SHORTCODE matches OR whose target URL matches. The server
 * combines the {@code code} and {@code url} filters with AND, so the palette
 * issues the term as two separate capped queries and merges them — this test
 * runs those exact {@link UrlMappingListRequest} queries against the real
 * in-process server (no mocks) and asserts that neither query alone yields the
 * OR result while their union does.
 */
class QuickSearchLookupE2ETest
    implements HasLogger {

  // Term shared by one link's shortcode and another link's URL.
  private static final String TERM = "alpha";
  private static final String CODE_MATCH = "qs-alpha";                        // shortcode carries the term
  private static final String URL_MATCH = "qs-zeta";                          // URL carries the term
  private static final String CODE_MATCH_URL = "https://quicksearch.example/zeta-one";
  private static final String URL_MATCH_URL = "https://quicksearch.example/alpha-two";

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;

  @BeforeAll
  static void startServer()
      throws Exception {
    UserDirectoryProvider.setDirectorySupplier(InMemoryUserDirectory::new);
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    baseUrlAdmin = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    adminToken = login();

    create(new ShortenRequest(CODE_MATCH_URL, CODE_MATCH, null, true));
    create(new ShortenRequest(URL_MATCH_URL, URL_MATCH, null, true));
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  @Test
  void aSingleTermFindsLinksByShortcodeOrUrlOnlyWhenBothQueriesAreMerged()
      throws Exception {
    String byCode = list(UrlMappingListRequest.builder()
        .codePart(TERM).size(8).sort("createdAt").dir("desc").build());
    String byUrl = list(UrlMappingListRequest.builder()
        .urlPart(TERM).size(8).sort("createdAt").dir("desc").build());

    // The code query surfaces only the shortcode match; the url query only the
    // URL match. This is exactly why one filter alone is not enough.
    assertTrue(byCode.contains(CODE_MATCH),
        "code query must surface the link whose shortcode contains the term");
    assertFalse(byCode.contains(URL_MATCH),
        "code query must not surface the link matched only by its URL");

    assertTrue(byUrl.contains(URL_MATCH),
        "url query must surface the link whose URL contains the term");
    assertFalse(byUrl.contains(CODE_MATCH),
        "url query must not surface the link matched only by its shortcode");

    // The palette's merged union is the OR result over shortcode + URL.
    assertTrue(byCode.contains(CODE_MATCH) && byUrl.contains(URL_MATCH),
        "the union of both queries is the OR result the palette relies on");
  }

  private static String login()
      throws Exception {
    var body = JsonUtils.toJson(Map.of("username", "admin", "password", "admin"));
    var req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_AUTH_LOGIN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(OK.code(), res.statusCode(), "admin login failed: " + res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }

  private static void create(ShortenRequest request)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_SHORTEN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(request), StandardCharsets.UTF_8))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(CREATED.code(), res.statusCode(),
        "link creation failed: " + res.statusCode() + " " + res.body());
  }

  private String list(UrlMappingListRequest request)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_LIST + "?" + request.toQueryString()))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .GET().build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(OK.code(), res.statusCode(), "list query failed: " + res.body());
    return res.body();
  }
}
