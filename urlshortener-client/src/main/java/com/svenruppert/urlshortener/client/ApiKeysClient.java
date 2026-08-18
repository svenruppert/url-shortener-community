package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.auth.ApiKeyCreateRequest;
import com.svenruppert.urlshortener.core.auth.ApiKeyCreatedResponse;
import com.svenruppert.urlshortener.core.auth.ApiKeySummary;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ME_API_KEYS;

/**
 * Client for the self-service API-key endpoints ({@code /api/me/api-keys}):
 * create, list and revoke the caller's own keys. Authenticates with the
 * caller's Bearer token (like {@link UsersClient}); the created key is the
 * plaintext {@code usk_…} returned once by the server.
 */
public class ApiKeysClient
    implements HasLogger {

  private final String baseUrl;
  private final HttpClient http;
  private Supplier<String> bearerToken = AuthContext::getGlobalToken;

  public ApiKeysClient() {
    this(ADMIN_SERVER_URL);
  }

  public ApiKeysClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public ApiKeysClient withBearerToken(Supplier<String> tokenSupplier) {
    this.bearerToken = tokenSupplier;
    return this;
  }

  /** Lists the caller's own API keys (never carries the key material). */
  public Result<List<ApiKeySummary>, String> list() {
    return send(builder(PATH_ME_API_KEYS).GET().build(), HttpStatus.OK,
        body -> List.of(JacksonJson.fromJson(body, ApiKeySummary[].class)));
  }

  /** Creates a new key with an optional label; the plaintext is returned once. */
  public Result<ApiKeyCreatedResponse, String> create(String label) {
    var request = builder(PATH_ME_API_KEYS)
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(JacksonJson.toJson(new ApiKeyCreateRequest(label))))
        .build();
    return send(request, HttpStatus.CREATED, body -> JacksonJson.fromJson(body, ApiKeyCreatedResponse.class));
  }

  /** Revokes one of the caller's own keys by id. */
  public Result<Boolean, String> revoke(String id) {
    var request = builder(PATH_ME_API_KEYS + "/" + id).DELETE().build();
    return send(request, HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  private HttpRequest.Builder builder(String path) {
    var b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10));
    var token = bearerToken.get();
    if (token != null && !token.isBlank()) {
      b.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    }
    return b;
  }

  private <T> Result<T, String> send(HttpRequest request, HttpStatus expected, Function<String, T> parser) {
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != expected.code()) {
        return Result.failure("Unexpected status " + response.statusCode() + ": " + response.body());
      }
      return Result.success(parser.apply(response.body()));
    } catch (IOException e) {
      logger().warn("I/O error calling {}: {}", request.uri(), e.getMessage());
      return Result.failure("I/O error: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failure("Interrupted");
    }
  }
}
