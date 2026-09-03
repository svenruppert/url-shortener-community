package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V00.17.00 P050 — read-scoping regression suite (SEC-M1 per-link statistics,
 * SEC-M2 listing/export). Real in-process server; seeded admin/user plus a
 * third regular user. A foreign USER must neither read another user's
 * statistics (403) nor see foreign links in any listing, count or export.
 */
class ReadScopingIdorE2ETest
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
    UserDirectoryProvider
        .setDirectorySupplier(
            InMemoryUserDirectory::new);
    server = new ShortenerServer();
    server.init(DEFAULT_SERVER_HOST, 0);
    baseUrlAdmin = ADMIN_SERVER_PROTOCOL + "://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    adminToken = login("admin", "admin");
    ownerToken = login("user", "user");
    var created = send(request(PATH_ADMIN_USERS, adminToken)
        .POST(body(JsonUtils.toJson(Map.of(
            "username", "eve3", "password", "eve-secret-3", "roles", List.of("USER"))))).build());
    assertEquals(CREATED.code(), created.statusCode(), created.body());
    attackerToken = login("eve3", "eve-secret-3");

    var linkRes = send(request(PATH_ADMIN_SHORTEN, ownerToken)
        .POST(body(JsonUtils.toJson(Map.of("url", "https://example.org/scoping")))).build());
    assertEquals(CREATED.code(), linkRes.statusCode(), linkRes.body());
    ownedCode = JsonUtils.fromJson(linkRes.body(), ShortUrlMapping.class).shortCode();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  // --- helpers --------------------------------------------------------------

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

  private static int statisticsStatus(String pathPrefix, String token, String query)
      throws Exception {
    return send(request(pathPrefix + "/" + ownedCode + query, token).GET().build()).statusCode();
  }

  // --- SEC-M1: per-link statistics ------------------------------------------

  @Test
  void foreignStatisticsReadsAreDenied()
      throws Exception {
    String today = "?date=" + LocalDate.now();
    String range = "?from=" + LocalDate.now().minusDays(7) + "&to=" + LocalDate.now();

    assertEquals(FORBIDDEN.code(), statisticsStatus(PATH_ADMIN_STATISTICS_COUNT, attackerToken, today),
        "foreign per-link count must be denied (SEC-M1)");
    assertEquals(FORBIDDEN.code(), statisticsStatus(PATH_ADMIN_STATISTICS_HOURLY, attackerToken, today));
    assertEquals(FORBIDDEN.code(), statisticsStatus(PATH_ADMIN_STATISTICS_DAILY, attackerToken, today));
    assertEquals(FORBIDDEN.code(), statisticsStatus(PATH_ADMIN_STATISTICS_TIMELINE, attackerToken, range));

    assertEquals(OK.code(), statisticsStatus(PATH_ADMIN_STATISTICS_COUNT, ownerToken, today),
        "the owner keeps reading their statistics");
    assertEquals(OK.code(), statisticsStatus(PATH_ADMIN_STATISTICS_COUNT, adminToken, today),
        "link:read:all bypasses");
  }

  // --- SEC-M2: listing + count ----------------------------------------------

  @Test
  void listingsAreOwnerScoped()
      throws Exception {
    var attackerList = send(request(PATH_ADMIN_LIST, attackerToken).GET().build());
    assertEquals(OK.code(), attackerList.statusCode());
    assertFalse(attackerList.body().contains(ownedCode),
        "a foreign USER must not see the owner's link in the listing (SEC-M2)");

    var attackerAll = send(request(PATH_ADMIN_LIST_ALL, attackerToken).GET().build());
    assertEquals(OK.code(), attackerAll.statusCode());
    assertFalse(attackerAll.body().contains(ownedCode),
        "the fixed list modes must be scoped too");

    var ownerList = send(request(PATH_ADMIN_LIST, ownerToken).GET().build());
    assertTrue(ownerList.body().contains(ownedCode), "the owner keeps seeing their link");

    var adminList = send(request(PATH_ADMIN_LIST, adminToken).GET().build());
    assertTrue(adminList.body().contains(ownedCode), "link:read:all keeps the full view");

    var attackerCount = send(request(PATH_ADMIN_LIST_COUNT, attackerToken).GET().build());
    assertEquals(OK.code(), attackerCount.statusCode());
    assertTrue(attackerCount.body().contains("\"total\":0"),
        "the count must reflect the scoped view: " + attackerCount.body());
  }

  // --- SEC-M2: link export + SEC-M1: statistics export ----------------------

  @Test
  void exportsAreOwnerScoped()
      throws Exception {
    var attackerExport = http.send(
        request(PATH_ADMIN_EXPORT,attackerToken).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(OK.code(), attackerExport.statusCode());
    assertFalse(unzipFirstEntry(attackerExport.body()).contains(ownedCode),
        "the link export must not leak foreign links (SEC-M2)");

    var ownerExport = http.send(
        request(PATH_ADMIN_EXPORT,ownerToken).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray());
    assertTrue(unzipFirstEntry(ownerExport.body()).contains(ownedCode),
        "the owner keeps exporting their own links");

    var attackerStats = http.send(
        request(PATH_ADMIN_STATISTICS_EXPORT, attackerToken).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(OK.code(), attackerStats.statusCode());
    assertFalse(unzipFirstEntry(attackerStats.body()).contains(ownedCode),
        "the statistics export must not leak foreign codes (SEC-M1)");
  }

  private static String unzipFirstEntry(byte[] zip)
      throws Exception {
    try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      assertNotNull(in.getNextEntry(), "zip export must contain an entry");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
