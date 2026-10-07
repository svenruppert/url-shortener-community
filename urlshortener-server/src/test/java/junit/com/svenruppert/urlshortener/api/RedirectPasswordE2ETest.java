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
import java.util.Map;

import static com.svenruppert.dependencies.core.net.HttpStatus.CREATED;
import static com.svenruppert.dependencies.core.net.HttpStatus.FOUND;
import static com.svenruppert.dependencies.core.net.HttpStatus.GONE;
import static com.svenruppert.dependencies.core.net.HttpStatus.NOT_FOUND;
import static com.svenruppert.dependencies.core.net.HttpStatus.NO_CONTENT;
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.dependencies.core.net.HttpStatus.UNAUTHORIZED;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_PROTOCOL;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_PROTOCOL;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_EDIT;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_LIST_ALL;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_SHORTEN;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_LOGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-link password protection on the redirect origin, against a real in-process
 * server (V00.22.00). Mirrors {@link RedirectMethodE2ETest}'s harness.
 *
 * <p>Pins the challenge/verify semantics that distinguish a password gate from
 * expiry: a protected link answers GET/HEAD with 401 (a challenge, not the flat
 * 410 that expiry yields), redirects only on a correct POSTed password, keeps
 * expiry (410) and inactive (404) precedence, and never exposes the hash in any
 * API response.
 */
class RedirectPasswordE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlRedirect;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;

  private static final String TARGET = "https://example.org/secret-target";
  private static final String PASSWORD = "s3cret-pw";

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
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    adminToken = login();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  // --- the checks -------------------------------------------------------------

  @Test
  void getOnProtectedLinkAnswersChallengeForm()
      throws Exception {
    String code = createProtected(PASSWORD);
    var res = redirect("GET", "/" + code);
    assertEquals(UNAUTHORIZED.code(), res.statusCode(), "a protected link challenges with 401");
    assertTrue(res.body().contains("name=\"password\""),
        "the challenge must carry a password input: " + res.body());
    assertTrue(res.body().contains("type=\"password\""), "input must be a password field");
  }

  @Test
  void headOnProtectedLinkIsUnauthorizedWithoutBody()
      throws Exception {
    String code = createProtected(PASSWORD);
    var res = redirect("HEAD", "/" + code);
    assertEquals(UNAUTHORIZED.code(), res.statusCode(), "HEAD cannot carry a password → locked");
    assertTrue(res.body() == null || res.body().isEmpty(), "HEAD must not send a body");
  }

  @Test
  void wrongPasswordIsRejected()
      throws Exception {
    String code = createProtected(PASSWORD);
    var res = postForm("/" + code, "password=wrong");
    assertEquals(UNAUTHORIZED.code(), res.statusCode(), "a wrong password stays on the challenge");
    assertTrue(res.body().contains("name=\"password\""), "wrong attempt re-renders the form");
  }

  @Test
  void correctPasswordRedirects()
      throws Exception {
    String code = createProtected(PASSWORD);
    var res = postForm("/" + code, "password=" + PASSWORD);
    assertEquals(FOUND.code(), res.statusCode(), "correct password redirects");
    assertEquals(TARGET, res.headers().firstValue("Location").orElse(null));
  }

  @Test
  void editWithEmptyPasswordRemovesProtection()
      throws Exception {
    String code = createProtected(PASSWORD);
    var edited = edit("{\"shortURL\":\"" + code + "\",\"url\":\"" + TARGET + "\",\"password\":\"\"}");
    assertEquals(NO_CONTENT.code(), edited.statusCode(), edited.body());
    var res = redirect("GET", "/" + code);
    assertEquals(FOUND.code(), res.statusCode(), "cleared password → plain redirect");
    assertEquals(TARGET, res.headers().firstValue("Location").orElse(null));
  }

  @Test
  void editWithoutPasswordKeepsProtection()
      throws Exception {
    String code = createProtected(PASSWORD);
    // Change only the URL; omit the password field entirely → protection kept.
    var edited = edit("{\"shortURL\":\"" + code + "\",\"url\":\"https://example.org/changed\"}");
    assertEquals(NO_CONTENT.code(), edited.statusCode(), edited.body());
    var res = redirect("GET", "/" + code);
    assertEquals(UNAUTHORIZED.code(), res.statusCode(), "omitted password must not drop protection");
  }

  @Test
  void unprotectedLinkStillRedirects()
      throws Exception {
    var created = createLink("{\"url\":\"" + TARGET + "\"}");
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    String code = codeFrom(created);
    var res = redirect("GET", "/" + code);
    assertEquals(FOUND.code(), res.statusCode(), "an unprotected link is unchanged");
    assertEquals(TARGET, res.headers().firstValue("Location").orElse(null));
  }

  @Test
  void expiryTakesPrecedenceOverPassword()
      throws Exception {
    var created = createLink("{\"url\":\"" + TARGET + "\",\"password\":\"" + PASSWORD
        + "\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}");
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    String code = codeFrom(created);
    assertEquals(GONE.code(), redirect("GET", "/" + code).statusCode(),
        "an expired protected link is 410, not a password challenge");
  }

  @Test
  void inactiveTakesPrecedenceOverPassword()
      throws Exception {
    var created = createLink("{\"url\":\"" + TARGET + "\",\"password\":\"" + PASSWORD
        + "\",\"active\":false}");
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    String code = codeFrom(created);
    assertEquals(NOT_FOUND.code(), redirect("GET", "/" + code).statusCode(),
        "an inactive protected link is 404, not a password challenge");
  }

  @Test
  void passwordAndHashNeverAppearInApiResponses()
      throws Exception {
    String secret = "topsecret-marker-xyz";
    var created = createLink("{\"url\":\"" + TARGET + "\",\"password\":\"" + secret + "\"}");
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    assertNoLeak(created.body(), secret);

    // and the list view
    var list = send(HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_LIST_ALL))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .GET().build());
    assertEquals(OK.code(), list.statusCode(), list.body());
    assertNoLeak(list.body(), secret);
  }

  private static void assertNoLeak(String body, String secret) {
    assertFalse(body.contains(secret), "plaintext password leaked: " + body);
    assertFalse(body.contains("passwordHash"), "passwordHash field leaked: " + body);
    assertFalse(body.toLowerCase().contains("argon2"), "password hash leaked: " + body);
  }

  // --- helpers ----------------------------------------------------------------

  private static String createProtected(String password)
      throws Exception {
    var created = createLink("{\"url\":\"" + TARGET + "\",\"password\":\"" + password + "\"}");
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    return codeFrom(created);
  }

  private static String codeFrom(HttpResponse<String> res) {
    return JsonUtils.fromJson(res.body(), ShortUrlMapping.class).shortCode();
  }

  private static HttpResponse<String> createLink(String json)
      throws Exception {
    return send(HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_SHORTEN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
        .build());
  }

  private static HttpResponse<String> edit(String json)
      throws Exception {
    return send(HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_EDIT))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
        .build());
  }

  private static HttpResponse<String> redirect(String method, String path)
      throws Exception {
    return send(HttpRequest.newBuilder(URI.create(baseUrlRedirect + path))
        .method(method, HttpRequest.BodyPublishers.noBody())
        .build());
  }

  private static HttpResponse<String> postForm(String path, String formBody)
      throws Exception {
    return send(HttpRequest.newBuilder(URI.create(baseUrlRedirect + path))
        .header(CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
        .build());
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
