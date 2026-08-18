package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.auth.AuditEntry;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_AUDIT;

/** Client for {@code GET /api/audit} (PRE-REBUILD-SCOPE Kap. 2.8 / 3.10). */
public class AuditClient
    implements HasLogger {

  private final String baseUrl;
  private final HttpClient http;
  private Supplier<String> bearerToken = AuthContext::getGlobalToken;

  public AuditClient() {
    this(ADMIN_SERVER_URL);
  }

  public AuditClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public AuditClient withBearerToken(Supplier<String> tokenSupplier) {
    this.bearerToken = tokenSupplier;
    return this;
  }

  /** Fetches audit entries, optionally filtered by subject, capped at {@code limit}. */
  public Result<List<AuditEntry>, String> fetch(String subject, int limit) {
    StringBuilder path = new StringBuilder(PATH_ADMIN_AUDIT).append("?limit=").append(limit);
    if (subject != null && !subject.isBlank()) {
      path.append("&subject=").append(URLEncoder.encode(subject, StandardCharsets.UTF_8));
    }
    var b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10)).GET();
    var token = bearerToken.get();
    if (token != null && !token.isBlank()) {
      b.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    }
    try {
      HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != HttpStatus.OK.code()) {
        return Result.failure("Unexpected status " + response.statusCode() + ": " + response.body());
      }
      return Result.success(List.of(JacksonJson.fromJson(response.body(), AuditEntry[].class)));
    } catch (IOException e) {
      logger().warn("audit fetch failed: {}", e.getMessage());
      return Result.failure("I/O error: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failure("Interrupted");
    }
  }
}
