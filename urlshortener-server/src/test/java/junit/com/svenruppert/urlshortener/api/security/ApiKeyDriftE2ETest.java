package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
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
import java.util.List;
import java.util.Map;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SEC-L2 regression lock (V00.17.00 P080): an API key stops working once the
 * subject's security version drifts past the snapshot captured at issue time
 * — the same kill switch that already invalidated Bearer tokens. Before this
 * cycle a role change never touched existing API keys.
 */
class ApiKeyDriftE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;
  private static long userId;
  private static String userApiKey;

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

    // A dedicated user so the drift bump cannot interfere with other tests.
    var created = send(request(PATH_ADMIN_USERS, adminToken)
        .POST(body(JsonUtils.toJson(Map.of(
            "username", "drifty", "password", "drift-secret-1", "roles", List.of("USER"))))).build());
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    userId = Long.parseLong(JsonUtils.parseJson(created.body()).get("id"));

    String userToken = login("drifty", "drift-secret-1");
    var keyRes = send(request(PATH_ME_API_KEYS, userToken)
        .POST(body("{\"label\":\"drift\"}")).build());
    assertEquals(CREATED.code(), keyRes.statusCode(), keyRes.body());
    userApiKey = JsonUtils.parseJson(keyRes.body()).get("key");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  private static String login(String username, String password)
      throws Exception {
    var res = send(request(PATH_AUTH_LOGIN, null)
        .POST(body(JsonUtils.toJson(Map.of("username", username, "password", password)))).build());
    assertEquals(OK.code(), res.statusCode(), res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }

  private static HttpRequest.Builder request(String path, String token) {
    var b = HttpRequest.newBuilder(URI.create(baseUrlAdmin + path))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8());
    if (token != null) b.header("Authorization", "Bearer " + token);
    return b;
  }

  private static HttpRequest.BodyPublisher body(String json) {
    return HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
  }

  private static HttpResponse<String> send(HttpRequest request)
      throws Exception {
    return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static int listWithKey()
      throws Exception {
    var b = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_ADMIN_LIST))
        .header(API_KEY_HEADER, userApiKey)
        .GET();
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
  }

  @Test
  void roleDriftInvalidatesTheApiKey()
      throws Exception {
    assertEquals(OK.code(), listWithKey(), "the fresh key must work");

    // Admin assigns ADMIN — VersionBumper.bump fires, the snapshot is stale.
    var bump = send(request(PATH_ADMIN_USERS + "/" + userId + "/roles/ADMIN", adminToken)
        .POST(HttpRequest.BodyPublishers.noBody()).build());
    assertEquals(OK.code(), bump.statusCode(), bump.body());

    assertEquals(UNAUTHORIZED.code(), listWithKey(),
        "SEC-L2: a version drift must invalidate existing API keys");
  }
}
