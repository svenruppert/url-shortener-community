package junit.com.svenruppert.urlshortener.api.handler.admin;

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
import java.util.Map;
import java.util.Optional;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V00.17.00 P020 — admin-only owner reassignment, exercised end-to-end against
 * the real in-process server with the seeded two-user fixture (admin id 1,
 * user id 2). Locks: USER cannot reach the endpoint (403), admin transfers
 * succeed and are recorded, unknown code/user answer 404.
 */
class LinkOwnerReassignE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;
  private static String userToken;

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
    userToken = login("user", "user");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  private static String login(String username, String password)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_AUTH_LOGIN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(
            JsonUtils.toJson(Map.of("username", username, "password", password)), StandardCharsets.UTF_8))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(OK.code(), res.statusCode(), "login failed: " + res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }

  private static HttpResponse<String> put(String path, String json, String token)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + path))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .header("Authorization", "Bearer " + token)
        .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
        .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static String createLinkAsAdmin()
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_SHORTEN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .header("Authorization", "Bearer " + adminToken)
        .POST(HttpRequest.BodyPublishers.ofString(
            JsonUtils.toJson(Map.of("url", "https://example.org/owned")), StandardCharsets.UTF_8))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(CREATED.code(), res.statusCode(), "create failed: " + res.body());
    return JsonUtils.fromJson(res.body(), ShortUrlMapping.class).shortCode();
  }

  @Test
  void adminTransfersOwnershipAndUserIsDenied()
      throws Exception {
    String code = createLinkAsAdmin();
    assertEquals(Optional.of(1L), LinkOwnershipStore.INSTANCE.owner(code),
        "create-time recording must assign the creating admin");

    // Plain USER lacks linkOwnerManage — the endpoint must be unreachable.
    var denied = put(PATH_ADMIN_LINK_OWNER + "/" + code,
        JsonUtils.toJson(Map.of("userId", "2")), userToken);
    assertEquals(FORBIDDEN.code(), denied.statusCode(),
        "USER must not reassign link owners: " + denied.body());
    assertEquals(Optional.of(1L), LinkOwnershipStore.INSTANCE.owner(code));

    // Admin transfer succeeds and is recorded.
    var ok = put(PATH_ADMIN_LINK_OWNER + "/" + code,
        JsonUtils.toJson(Map.of("userId", "2")), adminToken);
    assertEquals(OK.code(), ok.statusCode(), ok.body());
    assertEquals(Optional.of(2L), LinkOwnershipStore.INSTANCE.owner(code),
        "transfer must be recorded in the ownership store");
  }

  @Test
  void unknownCodeAndUnknownUserAnswer404()
      throws Exception {
    var noCode = put(PATH_ADMIN_LINK_OWNER + "/nope404",
        JsonUtils.toJson(Map.of("userId", "2")), adminToken);
    assertEquals(NOT_FOUND.code(), noCode.statusCode());

    String code = createLinkAsAdmin();
    var noUser = put(PATH_ADMIN_LINK_OWNER + "/" + code,
        JsonUtils.toJson(Map.of("userId", "999")), adminToken);
    assertEquals(NOT_FOUND.code(), noUser.statusCode());
    assertEquals(Optional.of(1L), LinkOwnershipStore.INSTANCE.owner(code),
        "failed transfer must not change ownership");
  }
}
