package junit.com.svenruppert.urlshortener.api;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortenRequest;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import java.util.Map;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UrlShortenerE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlREDIRECT;
  private static String baseUrlADMIN;
  private static HttpClient http;
  private static String adminToken;

  @BeforeAll
  static void startServer()
      throws Exception {
    // Keep the seeded admin/admin convention: install the in-memory
    // directory so the Stage-E bootstrap sees an existing admin and
    // never blocks with 503.
    UserDirectoryProvider
        .setDirectorySupplier(
            InMemoryUserDirectory::new);
    // Server auf Port 0 starten -> Ephemeral-Port, kollisionsfrei
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    int portRedirect = server.getPortRedirect();
    var portAdmin = server.getPortAdmin();
    baseUrlREDIRECT = DEFAULT_SERVER_PROTOCOL + "://" + DEFAULT_SERVER_HOST + ":" + portRedirect;
    baseUrlADMIN = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + portAdmin;
    http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)    // Redirects selbst prüfen
        .build();
    adminToken = login("admin", "admin");
  }

  /** Logs in against the admin server and returns the Bearer token. */
  private static String login(String username, String password)
      throws Exception {
    var body = JsonUtils.toJson(Map.of("username", username, "password", password));
    var req = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_LOGIN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(200, res.statusCode(), "admin login failed: " + res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  // --- Helper ---------------------------------------------------------------

  private HttpResponse<String> POST(String path, String json)
      throws Exception {
    var uri = URI.create(baseUrlADMIN + path);
    logger().info("POST Path - {}", uri);
    var req = HttpRequest.newBuilder(uri)
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
        .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private HttpResponse<String> GET(String path)
      throws Exception {
    var uri = URI.create(baseUrlADMIN + path);
    logger().info("GET Path - {}", uri);
    var req = HttpRequest.newBuilder(uri)
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .GET().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private HttpResponse<String> GET_REDIRECT(String path)
      throws Exception {
    var uri = URI.create(baseUrlREDIRECT + path);
    logger().info("GET REDIRECT Path - {}", uri);
    var req = HttpRequest.newBuilder(uri).GET().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private HttpResponse<String> DELETE(String path)
      throws Exception {
    var uri = URI.create(baseUrlADMIN + path);
    logger().info("DELETE Path - {}", uri);
    var req = HttpRequest.newBuilder(uri)
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .DELETE().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private HttpResponse<String> rawLogin(String username, String password)
      throws Exception {
    var body = JsonUtils.toJson(Map.of("username", username, "password", password));
    var req = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_LOGIN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  // --- Tests ----------------------------------------------------------------

  @Test
  @Order(500)
  void login_bruteForce_locksOutAfterRepeatedFailures()
      throws Exception {
    // A username that never exists -> never affects the seeded admin login.
    final String ghost = "ghost-bruteforce-user";
    int locked = -1;
    String retryAfter = null;
    for (int i = 0; i < 25 && locked < 0; i++) {
      var res = rawLogin(ghost, "wrong-password");
      if (res.statusCode() == TOO_MANY_REQUESTS.code()) {
        locked = res.statusCode();
        retryAfter = res.headers().firstValue("Retry-After").orElse(null);
      } else {
        assertEquals(UNAUTHORIZED.code(), res.statusCode(), "expected 401 before lockout");
      }
    }
    assertEquals(TOO_MANY_REQUESTS.code(), locked, "brute-force lockout (429) should trigger");
    assertTrue(retryAfter != null && !retryAfter.isBlank(), "Retry-After header present on lockout");
  }

  private String rawLoginToken(String username, String password)
      throws Exception {
    var res = rawLogin(username, password);
    assertEquals(200, res.statusCode(), "login failed: " + res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }

  private int meStatus(String token)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_ME))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
        .GET().build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
  }

  @Test
  @Order(510)
  void logoutAll_revokesEverySessionOfTheUser()
      throws Exception {
    // Use the seeded 'user' account so the shared admin token is unaffected.
    String t1 = rawLoginToken("user", "user");
    String t2 = rawLoginToken("user", "user");
    assertEquals(200, meStatus(t1));
    assertEquals(200, meStatus(t2));

    var req = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_LOGOUT_ALL))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + t1)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(NO_CONTENT.code(), res.statusCode(), "logout-all should return 204");

    assertEquals(UNAUTHORIZED.code(), meStatus(t1), "first user token revoked");
    assertEquals(UNAUTHORIZED.code(), meStatus(t2), "second user token revoked too");
    assertEquals(200, meStatus(adminToken), "admin session untouched");
  }

  @Test
  @Order(520)
  void adminHealth_okForAdmin_forbiddenForUser()
      throws Exception {
    var admin = GET(PATH_ADMIN_HEALTH);
    assertEquals(200, admin.statusCode(), "admin can read health");
    assertTrue(admin.body() != null && !admin.body().isBlank(), "health payload present");

    String userToken = rawLoginToken("user", "user");
    var req = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_ADMIN_HEALTH))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + userToken)
        .GET().build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(FORBIDDEN.code(), res.statusCode(), "user lacks storeInfo permission");
  }

  @Test
  @Order(530)
  void adminSessions_listAndRevokeByUserId()
      throws Exception {
    var list = GET(PATH_ADMIN_SESSIONS);
    assertEquals(200, list.statusCode(), "admin lists sessions");
    assertTrue(list.body().contains("\"userId\""), "session list payload present");

    String userToken = rawLoginToken("user", "user");
    assertEquals(200, meStatus(userToken));
    var meReq = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_ME))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + userToken)
        .GET().build();
    var me = http.send(meReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    String userId = JsonUtils.parseJson(me.body()).get("id");

    var del = DELETE(PATH_ADMIN_SESSIONS + "/" + userId);
    assertEquals(NO_CONTENT.code(), del.statusCode(), "admin revokes the user's sessions");
    assertEquals(UNAUTHORIZED.code(), meStatus(userToken), "user session revoked by admin");
  }

  @Test
  @Order(540)
  void apiKey_authenticatesRequests()
      throws Exception {
    String userToken = rawLoginToken("user", "user");
    var createReq = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_ME_API_KEYS))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + userToken)
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString("{\"label\":\"ci\"}", StandardCharsets.UTF_8))
        .build();
    var created = http.send(createReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(201, created.statusCode(), "key created: " + created.body());
    String apiKey = JsonUtils.parseJson(created.body()).get("key");
    assertTrue(apiKey != null && apiKey.startsWith("usk_"), "plaintext key returned once");

    var meWithKey = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_ME))
        .header("X-API-Key", apiKey)
        .GET().build();
    assertEquals(200, http.send(meWithKey, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
        "X-API-Key authenticates the request");

    var noAuth = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_ME)).GET().build();
    assertEquals(UNAUTHORIZED.code(),
        http.send(noAuth, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
        "no credentials -> 401");
  }

  @Test
  @Order(550)
  void aliasReservation_blocksCreationUntilReleased()
      throws Exception {
    final String alias = "promo-2027";
    var reserveReq = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_ADMIN_ALIASES + "/reserve"))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + adminToken)
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString("{\"alias\":\"" + alias + "\"}", StandardCharsets.UTF_8))
        .build();
    assertEquals(201, http.send(reserveReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
        "alias reserved");

    var blocked = POST(PATH_ADMIN_SHORTEN,
        JsonUtils.toJson(new ShortenRequest("https://example.com/p300", alias, null, true)));
    assertTrue(blocked.statusCode() != 201, "reserved alias cannot be created: " + blocked.statusCode());

    var release = DELETE(PATH_ADMIN_ALIASES + "/reserved/" + alias);
    assertEquals(NO_CONTENT.code(), release.statusCode(), "reservation released");

    var created = POST(PATH_ADMIN_SHORTEN,
        JsonUtils.toJson(new ShortenRequest("https://example.com/p300", alias, null, true)));
    assertEquals(201, created.statusCode(), "after release the alias is usable: " + created.body());
  }

  @Test
  @Order(560)
  void refreshToken_yieldsWorkingAccessToken()
      throws Exception {
    String access1 = rawLoginToken("user", "user");
    // issue a refresh token (authenticated with the access token)
    var rtReq = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_ME_REFRESH_TOKEN))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + access1)
        .POST(HttpRequest.BodyPublishers.noBody()).build();
    var rtRes = http.send(rtReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(200, rtRes.statusCode(), rtRes.body());
    String refreshToken = JsonUtils.parseJson(rtRes.body()).get("refreshToken");
    assertTrue(refreshToken != null && refreshToken.startsWith("rt_"), "refresh token issued");

    // exchange it for a new access token (public endpoint)
    var refReq = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_REFRESH))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(
            "{\"refreshToken\":\"" + refreshToken + "\"}", StandardCharsets.UTF_8)).build();
    var refRes = http.send(refReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(200, refRes.statusCode(), refRes.body());
    String access2 = JsonUtils.parseJson(refRes.body()).get("token");
    assertTrue(access2 != null && !access2.equals(access1), "a fresh, different access token");
    assertEquals(200, meStatus(access2), "refreshed access token authenticates");

    var bad = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_REFRESH))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString("{\"refreshToken\":\"rt_bogus\"}", StandardCharsets.UTF_8)).build();
    assertEquals(UNAUTHORIZED.code(),
        http.send(bad, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
        "invalid refresh token -> 401");
  }

  @Test
  @Order(570)
  void passwordReset_requestConfirmAndLogin()
      throws Exception {
    final String uname = "reset-victim";
    var create = POST(PATH_ADMIN_USERS,
        "{\"username\":\"" + uname + "\",\"password\":\"InitPass-1\",\"roles\":[\"USER\"]}");
    assertEquals(201, create.statusCode(), "dedicated user created: " + create.body());

    var reqReset = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_PASSWORD_RESET + "/request"))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + uname + "\"}", StandardCharsets.UTF_8))
        .build();
    var reqRes = http.send(reqReset, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(202, reqRes.statusCode(), reqRes.body());
    String devToken = JsonUtils.parseJson(reqRes.body()).get("devToken");
    assertTrue(devToken != null && devToken.startsWith("prt_"), "dev profile echoes the reset token");

    var confirm = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_PASSWORD_RESET + "/confirm"))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(
            "{\"token\":\"" + devToken + "\",\"newPassword\":\"NewPass-2\"}", StandardCharsets.UTF_8))
        .build();
    assertEquals(200, http.send(confirm, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
        "reset confirmed");

    assertEquals(200, rawLogin(uname, "NewPass-2").statusCode(), "new password works");
    assertEquals(UNAUTHORIZED.code(), rawLogin(uname, "InitPass-1").statusCode(), "old password rejected");
  }

  @Test
  @Order(580)
  void linkOwnership_onlyOwnerOrReadAllCanDelete()
      throws Exception {
    assertEquals(201, POST(PATH_ADMIN_USERS,
        "{\"username\":\"alice\",\"password\":\"AlicePass-1\",\"roles\":[\"USER\"]}").statusCode());
    assertEquals(201, POST(PATH_ADMIN_USERS,
        "{\"username\":\"bob\",\"password\":\"BobPass-1\",\"roles\":[\"USER\"]}").statusCode());
    String aliceTok = rawLoginToken("alice", "AlicePass-1");
    String bobTok = rawLoginToken("bob", "BobPass-1");

    final String code = "alice-link-p210";
    var create = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_ADMIN_SHORTEN))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + aliceTok)
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(
            JsonUtils.toJson(new ShortenRequest("https://example.com/alice", code, null, true)),
            StandardCharsets.UTF_8))
        .build();
    assertEquals(201, http.send(create, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
        "alice creates her link");

    var bobDel = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_ADMIN_DELETE + "/" + code))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + bobTok).DELETE().build();
    assertEquals(FORBIDDEN.code(),
        http.send(bobDel, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
        "bob is not the owner -> 403");

    var aliceDel = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_ADMIN_DELETE + "/" + code))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + aliceTok).DELETE().build();
    assertEquals(NO_CONTENT.code(),
        http.send(aliceDel, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
        "owner deletes her own link");
  }

  @Test
  @Order(590)
  void cors_allowsConfiguredOrigin_blocksOthers()
      throws Exception {
    System.setProperty("app.cors.allowed-origins", "https://trusted.example");
    try {
      var allowed = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_ME))
          .header("Origin", "https://trusted.example")
          .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
      var res = http.send(allowed, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      assertEquals(204, res.statusCode(), "preflight from trusted origin allowed");
      assertEquals("https://trusted.example",
          res.headers().firstValue("Access-Control-Allow-Origin").orElse(null),
          "CORS origin header echoed");

      var blocked = HttpRequest.newBuilder(URI.create(baseUrlADMIN + PATH_AUTH_ME))
          .header("Origin", "https://evil.example")
          .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
      assertEquals(403, http.send(blocked, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
          "preflight from untrusted origin blocked");
    } finally {
      System.clearProperty("app.cors.allowed-origins");
    }
  }

  @Test
  @Order(600)
  void eventStream_isNotServedByTheCommunityEdition()
      throws Exception {
    // The signed security-event stream became an enterprise feature in
    // V00.20.00 — its SSE transport turned commercial with the jCustos
    // open-core split, and a SIEM feed belongs next to the audit export.
    // The community server keeps signing, storing and auditing every event;
    // it just does not ship them out. Without the plug-in there is no route.
    var request = HttpRequest.newBuilder(URI.create(baseUrlADMIN + "/api/events/stream"))
        .GET().build();
    assertEquals(404,
        http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode(),
        "the community edition contributes no event-stream route");
  }

  @Test
  @Order(1)
  void shorten_returns201_andJson()
      throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest("https://example.com/x", "e2e-alias", null, null));
    var res = POST(PATH_ADMIN_SHORTEN, body);
    assertEquals(201, res.statusCode());
    assertTrue(res.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("application/json"));
    assertTrue(res.body().contains("\"originalUrl\":\"https://example.com/x\""));
    assertTrue(res.body().contains("\"shortCode\":\"e2e-alias\"") || res.body().contains("\"alias\":\"e2e-alias\""));
  }

  @Test
  @Order(2)
  void redirect_returns302_with_location()
      throws Exception {
    var res = GET_REDIRECT("/e2e-alias");
    assertEquals(302, res.statusCode());
    String loc = res.headers().firstValue("Location").orElse("");
    assertEquals("https://example.com/x", loc);
  }

  @Test
  @Order(3)
  void list_contains_mapping()
      throws Exception {
    var res = GET(PATH_ADMIN_LIST_ALL);
    assertEquals(200, res.statusCode());
    assertTrue(res.headers()
                   .firstValue(CONTENT_TYPE)
                   .orElse("")
                   .toLowerCase()
                   .contains(MediaType.APPLICATION_JSON.mime()));
    var body = res.body();
    logger().info("body {}", body);
    assertTrue(body.contains("shortCode") && body.contains("e2e-alias"));
  }

  @Test
  @Order(4)
  void duplicate_alias_conflict_409()
      throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest("https://example.com/other", "e2e-alias",null, null));
    var res = POST(PATH_ADMIN_SHORTEN, body);
    logger().info("expected response code - {} ", CONFLICT);
    assertEquals(CONFLICT.code(), res.statusCode(), "Doppelter Alias sollte Konflikt liefern");
    var responseBody = res.body();
    logger().info("responseBody - {}", responseBody);
    assertTrue(responseBody.contains("Alias already in use"));
  }

  @Test
  @Order(5)
  void invalid_json_returns400()
      throws Exception {
    var res = POST(PATH_ADMIN_SHORTEN, "{not-json}");

    logger().info("expected response code - {} ", BAD_REQUEST);
    var statusCode = res.statusCode();
    logger().info("statusCode {}", statusCode);
    assertEquals(BAD_REQUEST.code(), statusCode);
    var body = res.body();
    logger().info("invalid_json_returns500 - body {}", body);
    assertTrue(body.contains("Invalid JSON: Unexpected character"));
  }

  @Test
  @Order(6)
  void crlf_in_url_returns400()
      throws Exception {
    String body = JsonUtils.toJson(new ShortenRequest("https://ex\r\nample.com", "bad",null, null));
    var res = POST(PATH_ADMIN_SHORTEN, body);
    logger().info("response - {}", res.body());

    logger().info("expected response code - {} ", BAD_REQUEST);
    assertEquals(BAD_REQUEST.code(), res.statusCode());
    assertTrue(res.body().contains("URL must not contain whitespace or control characters"));
  }

  @Test
  @Order(7)
  void delete_returns204_and_second_delete_404_or_204_idempotent()
      throws Exception {
    var res1 = DELETE(PATH_ADMIN_DELETE + "/e2e-alias"); // oder /mappings/e2e-alias – je nach API

    logger().info("expected response code - {} ", NO_CONTENT);
    assertEquals(NO_CONTENT.code(), res1.statusCode());

    var res2 = DELETE(PATH_ADMIN_DELETE + "/e2e-alias");

    logger().info("expected response code - {} ", NOT_FOUND);

    assertEquals(NOT_FOUND.code(), res2.statusCode());
  }
}
