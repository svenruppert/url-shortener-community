package junit.com.svenruppert.urlshortener.api;

import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.core.JsonUtils;
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

import static com.svenruppert.dependencies.core.net.HttpStatus.BAD_REQUEST;
import static com.svenruppert.dependencies.core.net.HttpStatus.NOT_FOUND;
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.dependencies.core.net.HttpStatus.UNAUTHORIZED;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_PROTOCOL;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_AUDIT;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_PREFERENCES_COLUMNS_SINGLE;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_SHORTEN;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_TOGGLE_ACTIVE;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_LOGIN;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ME_API_KEYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Status codes and the headers they are required to carry.
 *
 * <p>Findings from the status-semantics audit of all 46 handlers, each pinned
 * where it is observable from outside: a 401 without a {@code WWW-Authenticate}
 * challenge, a 405 whose {@code Allow} omits a method the resource serves, a
 * 405 where the path — not the method — was the problem, a query parameter
 * silently replaced by a default, and a body on a HEAD response.
 */
class StatusSemanticsE2ETest {

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
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  @Test
  void anUnauthenticatedRequestIsToldHowToAuthenticate()
      throws Exception {
    // RFC 9110 §11.6.1 — a 401 MUST carry a challenge.
    var res = send(request(PATH_ME_API_KEYS, null).GET().build());
    assertEquals(UNAUTHORIZED.code(), res.statusCode(), res.body());
    String challenge = res.headers().firstValue("WWW-Authenticate").orElse(null);
    assertNotNull(challenge, "a 401 without WWW-Authenticate tells the client nothing");
    assertTrue(challenge.startsWith("Bearer"), "expected a Bearer challenge, got: " + challenge);
  }

  @Test
  void aHeadResponseCarriesNoBody()
      throws Exception {
    // Every admin response goes through JsonWriter, which used to pass a
    // content length regardless of method — the JDK logged a warning for each.
    var res = send(request(PATH_ME_API_KEYS, null)
        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build());
    assertEquals(UNAUTHORIZED.code(), res.statusCode());
    assertTrue(res.body() == null || res.body().isEmpty(),
        "HEAD must not carry a body, got: " + res.body());
  }

  @Test
  void anUnknownSubResourceIsNotFoundRatherThanMethodNotAllowed()
      throws Exception {
    // GET is a method this resource serves — so it was the path that did not
    // match, and that is a 404.
    var res = send(request(PATH_ME_API_KEYS + "/no-such-key-id", adminToken).GET().build());
    assertEquals(NOT_FOUND.code(), res.statusCode(),
        "the method was fine; the sub-resource does not exist: " + res.body());
  }

  @Test
  void aTrulyUnsupportedMethodStillGets405WithAllow()
      throws Exception {
    // Guards the test above: the 404 must not swallow genuine method errors.
    var res = send(request(PATH_ME_API_KEYS, adminToken)
        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build());
    assertEquals(405, res.statusCode(), res.body());
    assertNotNull(res.headers().firstValue("Allow").orElse(null),
        "a 405 must name the methods that work");
  }

  @Test
  void optionsNamesEveryMethodTheResourceServes()
      throws Exception {
    // The handler serves DELETE but used to advertise only "PUT, OPTIONS".
    var res = send(request(PATH_ADMIN_PREFERENCES_COLUMNS_SINGLE, adminToken)
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
    String allow = res.headers().firstValue("Allow").orElse("");
    assertTrue(allow.contains("DELETE"),
        "DELETE is served, so it belongs in Allow — got: " + allow);
    assertTrue(allow.contains("PUT"), "PUT must stay listed — got: " + allow);
  }

  @Test
  void anUnreadableLimitIsReportedInsteadOfSilentlyReplaced()
      throws Exception {
    // ?limit=abc used to be swallowed and turned into 500 entries.
    var res = send(request(PATH_ADMIN_AUDIT + "?limit=abc", adminToken).GET().build());
    assertEquals(BAD_REQUEST.code(), res.statusCode(),
        "a parameter the caller cannot have meant must be reported: " + res.body());
  }

  @Test
  void aValidLimitStillWorks()
      throws Exception {
    // Guards the test above against rejecting everything.
    var res = send(request(PATH_ADMIN_AUDIT + "?limit=5", adminToken).GET().build());
    assertEquals(OK.code(), res.statusCode(), res.body());
  }

  @Test
  void togglingAnAliasThatDoesNotExistIsNotFound()
      throws Exception {
    // The store reports a missing alias as an ordinary failure, and the handler
    // turned that into 400 — a malformed-request answer to a perfectly formed
    // request. Its own Javadoc promised 404, and DeleteMappingHandler and
    // EditMappingHandler answer the same case that way.
    var res = send(request(PATH_ADMIN_TOGGLE_ACTIVE, adminToken)
        .PUT(HttpRequest.BodyPublishers.ofString(
            JsonUtils.toJson(Map.of("shortCode", "definitely-not-a-code", "active", "false")),
            StandardCharsets.UTF_8)).build());
    assertEquals(NOT_FOUND.code(), res.statusCode(),
        "a well-formed request for something that does not exist is a 404: " + res.body());
  }

  @Test
  void anAliasCanBeToggledWhateverItsSpelling()
      throws Exception {
    // Aliases are stored lower-cased (AliasPolicy.normalize). delete,
    // findByShortCode and existsByCode all normalise before looking up —
    // toggleActive was the one that did not, so a mixed-case spelling reported
    // "not found" for a link that resolved fine everywhere else.
    String alias = "ToggleCaseAlias";
    var created = send(request(PATH_ADMIN_SHORTEN, adminToken)
        .POST(HttpRequest.BodyPublishers.ofString(
            JsonUtils.toJson(Map.of("url", "https://example.org/case", "shortURL", alias)),
            StandardCharsets.UTF_8)).build());
    assertEquals(201, created.statusCode(), created.body());

    var res = send(request(PATH_ADMIN_TOGGLE_ACTIVE, adminToken)
        .PUT(HttpRequest.BodyPublishers.ofString(
            JsonUtils.toJson(Map.of("shortCode", alias, "active", "false")),
            StandardCharsets.UTF_8)).build());
    assertEquals(OK.code(), res.statusCode(),
        "the alias exists — spelling must not decide whether it can be toggled: " + res.body());
  }

  // --- helpers ----------------------------------------------------------------

  private static String login()
      throws Exception {
    var res = send(request(PATH_AUTH_LOGIN, null)
        .POST(HttpRequest.BodyPublishers.ofString(
            JsonUtils.toJson(Map.of("username", "admin", "password", "admin")),
            StandardCharsets.UTF_8)).build());
    assertEquals(OK.code(), res.statusCode(), res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }

  private static HttpRequest.Builder request(String path, String token) {
    var b = HttpRequest.newBuilder(URI.create(baseUrlAdmin + path))
        .timeout(Duration.ofSeconds(10))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8());
    if (token != null) b.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    return b;
  }

  private static HttpResponse<String> send(HttpRequest request)
      throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }
}
