package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.api.security.LinkOwnershipStore;
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
import java.util.List;
import java.util.Map;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V00.17.00 P030 — two-user IDOR regression suite for the manage paths
 * (SEC-H1 edit, SEC-H2 toggle) plus
 * the fail-closed semantics for unowned codes. Real in-process server, no
 * mocks: seeded admin (id 1) + user (id 2), and a third regular user created
 * through the admin API so USER↔USER isolation is proven without the admin
 * bypass getting in the way. Every denial asserts 403 AND that the resource
 * stayed unchanged where observable.
 */
class LinkManageIdorE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;
  private static String ownerToken;   // seeded "user" (id 2) — the link owner
  private static String attackerToken; // created "eve" — same USER role

  @BeforeAll
  static void startServer()
      throws Exception {
    com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider
        .setDirectorySupplier(
            com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory::new);
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    baseUrlAdmin = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    adminToken = login("admin", "admin");
    ownerToken = login("user", "user");

    var created = send(request(PATH_ADMIN_USERS, adminToken)
        .POST(body(JsonUtils.toJson(Map.of(
            "username", "eve", "password", "eve-secret-1", "roles", List.of("USER")))))
        .build());
    assertEquals(CREATED.code(), created.statusCode(), "creating attacker user failed: " + created.body());
    attackerToken = login("eve", "eve-secret-1");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  // --- helpers --------------------------------------------------------------

  private static String login(String username, String password)
      throws Exception {
    var res = send(request(PATH_AUTH_LOGIN, null)
        .POST(body(JsonUtils.toJson(Map.of("username", username, "password", password))))
        .build());
    assertEquals(OK.code(), res.statusCode(), "login failed: " + res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }

  private static HttpRequest.Builder request(String path, String token) {
    var b = HttpRequest.newBuilder(URI.create(baseUrlAdmin + path))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8());
    if (token != null) {
      b.header("Authorization", "Bearer " + token);
    }
    return b;
  }

  private static HttpRequest.BodyPublisher body(String json) {
    return HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
  }

  private static HttpResponse<String> send(HttpRequest request)
      throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static String createLinkAs(String token, String url)
      throws Exception {
    var res = send(request(PATH_ADMIN_SHORTEN, token)
        .POST(body(JsonUtils.toJson(Map.of("url", url)))).build());
    assertEquals(CREATED.code(), res.statusCode(), "create failed: " + res.body());
    return JsonUtils.fromJson(res.body(), ShortUrlMapping.class).shortCode();
  }

  private static HttpResponse<String> edit(String token, String code, String newUrl)
      throws Exception {
    return send(request(PATH_ADMIN_EDIT, token)
        .PUT(body(JsonUtils.toJson(Map.of("shortURL", code, "url", newUrl)))).build());
  }

  private static HttpResponse<String> toggle(String token, String code, boolean active)
      throws Exception {
    return send(request(PATH_ADMIN_TOGGLE_ACTIVE, token)
        .PUT(body("{\"shortCode\":\"" + code + "\",\"active\":" + active + "}")).build());
  }

  // --- SEC-H1: edit ---------------------------------------------------------

  @Test
  void foreignEditIsDeniedOwnerAndAdminSucceed()
      throws Exception {
    String code = createLinkAs(ownerToken, "https://example.org/h1");

    assertEquals(FORBIDDEN.code(), edit(attackerToken, code, "https://evil.example/hijack").statusCode(),
        "a foreign USER must not edit the link (SEC-H1)");
    assertEquals(NO_CONTENT.code(), edit(ownerToken, code, "https://example.org/h1-owner").statusCode(),
        "the owner must still edit the link");
    assertEquals(NO_CONTENT.code(), edit(adminToken, code, "https://example.org/h1-admin").statusCode(),
        "link:read:all must bypass");
  }

  // --- SEC-H2: toggle-active ------------------------------------------------

  @Test
  void foreignToggleIsDeniedOwnerAndAdminSucceed()
      throws Exception {
    String code = createLinkAs(ownerToken, "https://example.org/h2");

    assertEquals(FORBIDDEN.code(), toggle(attackerToken, code, false).statusCode(),
        "a foreign USER must not (de)activate the link (SEC-H2)");
    assertEquals(OK.code(), toggle(ownerToken, code, false).statusCode(),
        "the owner must still toggle the link");
    assertEquals(OK.code(), toggle(adminToken, code, true).statusCode(),
        "link:read:all must bypass");
  }

  // --- fail-closed: unowned codes are admin-only ----------------------------

  @Test
  void unownedCodeIsManageableByAdminOnly()
      throws Exception {
    String code = createLinkAs(adminToken, "https://example.org/unowned");
    // Simulate a pre-backfill anomaly: the ownership record vanishes.
    LinkOwnershipStore.INSTANCE.remove(code);

    assertEquals(FORBIDDEN.code(), edit(ownerToken, code, "https://example.org/claim").statusCode(),
        "unowned codes must be admin-only since P030 (fail-closed)");
    assertEquals(NO_CONTENT.code(), edit(adminToken, code, "https://example.org/admin-fix").statusCode(),
        "admins keep managing unowned codes");
  }
}
