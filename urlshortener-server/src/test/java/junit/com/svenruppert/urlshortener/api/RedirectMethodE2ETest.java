package junit.com.svenruppert.urlshortener.api;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static com.svenruppert.dependencies.core.net.HttpStatus.CREATED;
import static com.svenruppert.dependencies.core.net.HttpStatus.FOUND;
import static com.svenruppert.dependencies.core.net.HttpStatus.METHOD_NOT_ALLOWED;
import static com.svenruppert.dependencies.core.net.HttpStatus.NOT_FOUND;
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_PROTOCOL;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_PROTOCOL;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_SHORTEN;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_STATISTICS_COUNT;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_LOGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Method handling on the redirect origin, against a real in-process server.
 *
 * <p>Link checkers probe short links with HEAD before they show a preview or
 * release a click — Microsoft SafeLinks and Teams, Amazon Quick, and the
 * WordPress link checker all do. Three months of production access logs showed
 * <b>1220 HEAD requests, every single one answered 405</b> and not one 302, so
 * every one of those checkers considered the links broken. These tests pin the
 * corrected behavior: HEAD mirrors GET without a body, the bare origin answers
 * 404 rather than 400, an unsupported method names what is allowed, and a HEAD
 * probe never counts as a click.
 */
class RedirectMethodE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlRedirect;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;
  private static String shortCode;

  private static final String TARGET_URL = "https://example.org/head-method-check";

  @BeforeAll
  static void startServer()
      throws Exception {
    UserDirectoryProvider.setDirectorySupplier(InMemoryUserDirectory::new);
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    baseUrlRedirect = DEFAULT_SERVER_PROTOCOL + "://" + DEFAULT_SERVER_HOST + ":" + server.getPortRedirect();
    baseUrlAdmin = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)   // the redirect is the subject under test
        .build();
    adminToken = login();

    var created = send(HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_SHORTEN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .POST(HttpRequest.BodyPublishers.ofString(
            JsonUtils.toJson(Map.of("url", TARGET_URL)), StandardCharsets.UTF_8))
        .build());
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    shortCode = JsonUtils.fromJson(created.body(), ShortUrlMapping.class).shortCode();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  // --- the actual checks ------------------------------------------------------

  @Test
  void headOnLiveShortCodeMirrorsGetWithoutABody()
      throws Exception {
    var get = redirect("GET", "/" + shortCode);
    var head = redirect("HEAD", "/" + shortCode);

    assertEquals(FOUND.code(), head.statusCode(),
        "HEAD must answer like GET — a link checker treats anything else as a broken link");
    assertEquals(get.statusCode(), head.statusCode(), "HEAD and GET must agree on the status");
    assertEquals(TARGET_URL, head.headers().firstValue("Location").orElse(null),
        "HEAD must carry the same Location as GET");
    assertEquals(get.headers().firstValue("Location").orElse(null),
        head.headers().firstValue("Location").orElse(null),
        "HEAD and GET must agree on the target");
    assertTrue(head.body() == null || head.body().isEmpty(),
        "HEAD must not send a body, got: " + head.body());
  }

  @Test
  void headOnUnknownShortCodeIsNotFound()
      throws Exception {
    var head = redirect("HEAD", "/no-such-code-here");
    assertEquals(NOT_FOUND.code(), head.statusCode(),
        "an unknown code is 404 for HEAD just as it is for GET");
    assertTrue(head.body() == null || head.body().isEmpty(), "no body on HEAD");
  }

  @Test
  void bareOriginIsNotFoundForGetAndHead()
      throws Exception {
    // Regression: the origin used to answer 400 to a perfectly well-formed
    // request. Browsers, Googlebot and GPTBot all land here.
    assertEquals(NOT_FOUND.code(), redirect("GET", "/").statusCode(),
        "the bare origin carries no short code — that is 404, not a malformed request");
    assertEquals(NOT_FOUND.code(), redirect("HEAD", "/").statusCode(),
        "same answer for HEAD");
  }

  @Test
  void unsupportedMethodIsRejectedAndNamesWhatIsAllowed()
      throws Exception {
    var res = redirect("POST", "/" + shortCode);
    assertEquals(METHOD_NOT_ALLOWED.code(), res.statusCode(), "POST is not a redirect method");
    // RFC 9110 §15.5.6: a 405 MUST name the supported methods.
    String allow = res.headers().firstValue("Allow").orElse(null);
    assertEquals("GET, HEAD", allow, "the 405 must tell the caller which methods work");
  }

  @Test
  void headProbeIsNotCountedAsAClick()
      throws Exception {
    // SafeLinks alone sent 1047 probes in three months. Counting those would
    // inflate exactly the raw events the statistics and analytics views read.
    String before = statisticsCount();

    assertEquals(FOUND.code(), redirect("HEAD", "/" + shortCode).statusCode());
    assertEquals(before, statisticsCount(),
        "a HEAD probe is a link check, not a visit — it must not move the counter");

    assertEquals(FOUND.code(), redirect("GET", "/" + shortCode).statusCode());
    assertNotEquals(before, statisticsCount(),
        "a real GET must still be counted, otherwise this test proves nothing");
  }

  // --- helpers ----------------------------------------------------------------

  private static HttpResponse<String> redirect(String method, String path)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrlRedirect + path))
        .method(method, HttpRequest.BodyPublishers.noBody())
        .build();
    return send(req);
  }

  /** Raw count payload for the link — compared as a whole, so its shape may change freely. */
  private static String statisticsCount()
      throws Exception {
    var req = HttpRequest.newBuilder(
            URI.create(baseUrlAdmin + PATH_ADMIN_STATISTICS_COUNT + "/" + shortCode
                       + "?date=" + LocalDate.now()))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .GET().build();
    var res = send(req);
    assertEquals(OK.code(), res.statusCode(), "statistics readable: " + res.body());
    return res.body();
  }

  private static String login()
      throws Exception {
    var body = JsonUtils.toJson(Map.of("username", "admin", "password", "admin"));
    var res = send(HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_AUTH_LOGIN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build());
    assertEquals(OK.code(), res.statusCode(), "admin login failed: " + res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }

  private static HttpResponse<String> send(HttpRequest request)
      throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }
}
