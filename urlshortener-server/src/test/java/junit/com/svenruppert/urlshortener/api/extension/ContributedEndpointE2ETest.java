package junit.com.svenruppert.urlshortener.api.extension;

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
import java.util.Map;
import com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;

import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.dependencies.core.net.HttpStatus.UNAUTHORIZED;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V00.18.00 P030 — the open-core server seam end-to-end (no mocks): the
 * test-scope {@link PingContribution} is discovered via ServiceLoader,
 * registered on the real in-process server behind the standard security
 * pipeline, reads from the real store, and answers authenticated requests
 * only. Without the plug-in on the classpath none of this exists — proven by
 * the production build containing no such route.
 */
class ContributedEndpointE2ETest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;
  private static String adminToken;

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

    var res = http.send(HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_AUTH_LOGIN))
            .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
            .POST(HttpRequest.BodyPublishers.ofString(
                JsonUtils.toJson(Map.of("username", "admin", "password", "admin")),
                StandardCharsets.UTF_8)).build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    adminToken = JsonUtils.parseJson(res.body()).get("token");
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  @Test
  void contributedEndpointRunsBehindTheSecurityPipeline()
      throws Exception {
    var unauthenticated = http.send(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PingContribution.PATH)).GET().build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(UNAUTHORIZED.code(), unauthenticated.statusCode(),
        "the contributed endpoint must sit behind the standard security pipeline");

    var authenticated = http.send(
        HttpRequest.newBuilder(URI.create(baseUrlAdmin + PingContribution.PATH))
            .header("Authorization", "Bearer " + adminToken).GET().build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    assertEquals(OK.code(), authenticated.statusCode(), authenticated.body());
    assertTrue(authenticated.body().contains("\"feature\":\"extension-ping\""), authenticated.body());
    assertTrue(authenticated.body().contains("\"links\":"),
        "the contribution must read from the real store via ServerContext");
  }
}
