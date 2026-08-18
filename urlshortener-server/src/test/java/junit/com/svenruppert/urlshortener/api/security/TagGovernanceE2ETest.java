package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
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
import static org.junit.jupiter.api.Assertions.*;

/**
 * V00.17.00 P060 — tag governance (rest of SEC-L1, concept DECISION 2): the
 * registry is ONE global taxonomy whose mutations (create/rename/replace/
 * delete) are admin-only, while reading it and attaching tags to OWN links
 * stays a USER operation. Cross-tenant attach attempts are silently skipped,
 * mirroring the campaign batch-assign semantics.
 */
class TagGovernanceE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;
  private static String ownerToken;
  private static String attackerToken;
  private static String ownedCode;

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
            "username", "eve4", "password", "eve-secret-4", "roles", List.of("USER"))))).build());
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    attackerToken = login("eve4", "eve-secret-4");

    var linkRes = send(request(PATH_ADMIN_SHORTEN, ownerToken)
        .POST(body(JsonUtils.toJson(Map.of("url", "https://example.org/tags")))).build());
    assertEquals(CREATED.code(), linkRes.statusCode(), linkRes.body());
    ownedCode = JsonUtils.fromJson(linkRes.body(), ShortUrlMapping.class).shortCode();
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

  private static int postTags(String action, String json, String token)
      throws Exception {
    return send(request(PATH_ADMIN_TAGS + "/" + action, token).POST(body(json)).build()).statusCode();
  }

  @Test
  void registryMutationsAreAdminOnly()
      throws Exception {
    assertEquals(FORBIDDEN.code(), postTags("create", "{\"name\":\"evil\"}", attackerToken),
        "USER must not create registry tags (SEC-L1 / DECISION 2)");
    assertEquals(FORBIDDEN.code(), postTags("rename", "{\"from\":\"a\",\"to\":\"b\"}", attackerToken));
    assertEquals(FORBIDDEN.code(), postTags("replace", "{\"from\":\"a\",\"to\":[\"b\"]}", attackerToken));
    assertEquals(FORBIDDEN.code(), postTags("delete", "{\"tag\":\"a\"}", attackerToken));

    assertEquals(NO_CONTENT.code(), postTags("create", "{\"name\":\"governance\"}", adminToken),
        "admin keeps managing the registry");
    assertEquals(NO_CONTENT.code(), postTags("rename",
        "{\"from\":\"governance\",\"to\":\"governance2\"}", adminToken));

    assertEquals(OK.code(),
        send(request(PATH_ADMIN_TAGS, attackerToken).GET().build()).statusCode(),
        "the registry stays readable for every USER");
  }

  @Test
  void attachIsOwnerGatedPerLink()
      throws Exception {
    // The attacker tries to tag the owner's link — 204, but silently skipped.
    assertEquals(NO_CONTENT.code(), postTags("assign",
        JsonUtils.toJson(Map.of("tag", "sneaky", "shortCodes", List.of(ownedCode))), attackerToken));
    var metaAfterAttack = send(request(PATH_ADMIN_LINK_META + "/" + ownedCode, ownerToken).GET().build());
    assertEquals(OK.code(), metaAfterAttack.statusCode());
    assertFalse(metaAfterAttack.body().contains("sneaky"),
        "a foreign USER must not attach tags to the owner's link (SEC-L1)");

    // The owner tags their own link — attached.
    assertEquals(NO_CONTENT.code(), postTags("assign",
        JsonUtils.toJson(Map.of("tag", "mine", "shortCodes", List.of(ownedCode))), ownerToken));
    var metaAfterOwner = send(request(PATH_ADMIN_LINK_META + "/" + ownedCode, ownerToken).GET().build());
    assertTrue(metaAfterOwner.body().contains("mine"),
        "the owner keeps tagging their own links");
  }
}
