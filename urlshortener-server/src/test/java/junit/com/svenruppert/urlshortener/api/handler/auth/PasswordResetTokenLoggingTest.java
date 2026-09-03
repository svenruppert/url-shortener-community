package junit.com.svenruppert.urlshortener.api.handler.auth;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.core.JsonUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;

import static com.svenruppert.dependencies.core.net.HttpStatus.ACCEPTED;
import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SEC-H4 regression lock: the password-reset token is a credential and must
 * never appear in the log. The development profile still echoes it as
 * {@code devToken}, which is exactly what lets this test know the secret it
 * asserts the log does NOT contain. slf4j-simple writes to the current
 * {@code System.err} (cacheOutputStream defaults to false), so swapping the
 * stream around the request captures the server's log output for real.
 */
class PasswordResetTokenLoggingTest
    implements HasLogger {

  private static ShortenerServer server;
  private static String baseUrlAdmin;
  private static HttpClient http;

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
  }

  @AfterAll
  static void stopServer() {
    if (server != null) server.shutdown();
  }

  @Test
  void resetTokenIsIssuedButNeverLogged()
      throws Exception {
    var captured = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    HttpResponse<String> res;
    try {
      System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
      var req = HttpRequest.newBuilder(URI.create(baseUrlAdmin + PATH_PASSWORD_RESET + "/request"))
          .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
          .POST(HttpRequest.BodyPublishers.ofString(
              JsonUtils.toJson(Map.of("username", "admin")), StandardCharsets.UTF_8))
          .build();
      res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } finally {
      System.setErr(originalErr);
    }
    assertEquals(ACCEPTED.code(), res.statusCode(), "reset request must be accepted: " + res.body());

    String devToken = JsonUtils.parseJson(res.body()).get("devToken");
    assertNotNull(devToken, "development profile must echo the token for tests");
    assertFalse(devToken.isBlank(), "issued token must not be blank");

    String log = captured.toString(StandardCharsets.UTF_8);
    assertTrue(log.contains("password reset requested"),
        "the reset event itself must still be logged");
    assertFalse(log.contains(devToken),
        "SEC-H4: the reset token must never appear in the log");
  }
}
