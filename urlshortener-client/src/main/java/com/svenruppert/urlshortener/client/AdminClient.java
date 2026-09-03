package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.StoreInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class AdminClient
    implements HasLogger {

  private static final HttpClient SHARED_HTTP = HttpClient.newHttpClient();

  private final URI serverBaseAdmin;
  private Supplier<String> bearerToken = AuthContext::getGlobalToken;

  public AdminClient() {
    this(ADMIN_SERVER_URL);
  }

  public AdminClient(String serverBaseUrlAdmin) {
    var urlToServerAdmin = serverBaseUrlAdmin.endsWith("/")
        ? serverBaseUrlAdmin
        : serverBaseUrlAdmin + "/";

    logger().info("urlToServer ADMIN - {}", urlToServerAdmin);
    this.serverBaseAdmin = URI.create(urlToServerAdmin);
  }

  /** Session-scoped token source (e.g. Vaadin session) instead of the global default. */
  public AdminClient withBearerToken(Supplier<String> tokenSupplier) {
    this.bearerToken = tokenSupplier;
    return this;
  }

  public StoreInfo getStoreInfo()
      throws IOException {
    URL url = serverBaseAdmin.resolve(PATH_ADMIN_STORE_INFO).toURL();
    var con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    con.setConnectTimeout(3000);
    con.setReadTimeout(3000);
    var token = bearerToken.get();
    if (token != null && !token.isBlank()) {
      con.setRequestProperty(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    }

    try (var in = con.getInputStream()) {
      var json = new String(in.readAllBytes(), UTF_8);
      logger().info("getStoreInfo - json response from server - {}", json);
      var storeInfo = JsonUtils.fromJson(json, StoreInfo.class);
      logger().info("getStoreInfo - storeInfo {}", storeInfo);
      return storeInfo;
    } catch (Exception e) {
      logger().warn(" getStoreInfo - Failed {}", e.getMessage());
      throw new IOException(e.getMessage());
    }
  }

  /**
   * Transfers ownership of a short link to another user
   * ({@code PUT /api/admin/links/owner/{shortCode}}, admin-only —
   * V00.17.00 P020). The transfer is written to the security audit log
   * server-side.
   */
  public Result<Boolean, String> transferLinkOwner(String shortCode, long newOwnerUserId) {
    try {
      var uri = serverBaseAdmin.resolve(
          PATH_ADMIN_LINK_OWNER + "/" + URLEncoder.encode(shortCode, UTF_8));
      var builder = HttpRequest.newBuilder(uri)
          .timeout(Duration.ofSeconds(10))
          .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
          .PUT(HttpRequest.BodyPublishers.ofString(
              JsonUtils.toJson(Map.of("userId", String.valueOf(newOwnerUserId))), UTF_8));
      var token = bearerToken.get();
      if (token != null && !token.isBlank()) {
        builder.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
      }
      HttpResponse<String> response = SHARED_HTTP
          .send(builder.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
      if (response.statusCode() != HttpStatus.OK.code()) {
        return Result.failure("Unexpected status " + response.statusCode() + ": " + response.body());
      }
      return Result.success(Boolean.TRUE);
    } catch (IOException e) {
      logger().warn("transferLinkOwner failed - {}", e.getMessage());
      return Result.failure("I/O error: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failure("interrupted");
    }
  }

}
