package junit.com.svenruppert.urlshortener.api;

import com.svenruppert.dependencies.core.logger.HasLogger;
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
import static com.svenruppert.dependencies.core.net.HttpStatus.OK;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_PROTOCOL;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.DEFAULT_SERVER_HOST;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_LOGIN;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_REFRESH;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ME_API_KEYS;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_PASSWORD_RESET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A malformed JSON body must be answered — with 400, and never with silence.
 *
 * <p>Jackson 3 reports parse failures as unchecked {@code JacksonException}
 * ({@code extends RuntimeException}). Handlers that caught only
 * {@code IOException} therefore let it escape {@code handle()}, and the JDK
 * {@code HttpServer} closed the connection without sending a status line.
 * Measured against a real server before the fix:
 *
 * <pre>
 * POST /api/refresh                 -> no response (connection closed)
 * POST /api/password-reset/request  -> no response (connection closed)
 * POST /api/login                   -> HTTP 500
 * </pre>
 *
 * <p>All three are reachable without authentication. These tests pin the
 * corrected behavior; {@link junit.com.svenruppert.urlshortener.api.filter.FailSafeFilterTest}
 * covers the net that catches whatever still escapes.
 */
class MalformedBodyE2ETest
    implements HasLogger {

  /** Well-formed HTTP, unparseable payload — the caller's mistake, so 400. */
  private static final String GARBAGE = "{ this is not json";

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
  void refreshAnswersBadRequestInsteadOfDroppingTheConnection()
      throws Exception {
    assertBadJson(post(PATH_AUTH_REFRESH, GARBAGE, null));
  }

  @Test
  void loginAnswersBadRequestNotServerError()
      throws Exception {
    // Used to be 500: the handler's catch(RuntimeException) treated the
    // caller's malformed body as a fault of the server.
    assertBadJson(post(PATH_AUTH_LOGIN, GARBAGE, null));
  }

  @Test
  void passwordResetAnswersBadRequest()
      throws Exception {
    assertBadJson(post(PATH_PASSWORD_RESET + "/request", GARBAGE, null));
  }

  @Test
  void authenticatedEndpointAnswersBadRequestToo()
      throws Exception {
    assertBadJson(post(PATH_ME_API_KEYS, GARBAGE, adminToken));
  }

  @Test
  void aBlankBodyIsAFieldErrorNotAParseError()
      throws Exception {
    // An empty body parses fine; the handler's own required-field check must
    // still produce the more specific message rather than "invalid_json".
    var res = post(PATH_AUTH_REFRESH, "", null);
    assertEquals(BAD_REQUEST.code(), res.statusCode(), res.body());
    assertTrue(res.body() != null && !res.body().contains("invalid_json"),
        "a blank body is a missing field, not malformed JSON: " + res.body());
  }

  // --- helpers ----------------------------------------------------------------

  private static void assertBadJson(HttpResponse<String> res) {
    assertEquals(BAD_REQUEST.code(), res.statusCode(),
        "a malformed body is the caller's error: " + res.body());
    assertTrue(res.body() != null && res.body().contains("invalid_json"),
        "the answer should name the cause, got: " + res.body());
  }

  private static HttpResponse<String> post(String path, String body, String token)
      throws Exception {
    var req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + path))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .timeout(Duration.ofSeconds(10));
    if (token != null) req.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    return http.send(req.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                     HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static String login()
      throws Exception {
    var body = JsonUtils.toJson(Map.of("username", "admin", "password", "admin"));
    var res = post(PATH_AUTH_LOGIN, body, null);
    assertEquals(OK.code(), res.statusCode(), "admin login failed: " + res.body());
    return JsonUtils.parseJson(res.body()).get("token");
  }
}
