package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.auth.BootstrapStatusResponse;
import com.svenruppert.urlshortener.core.auth.ChangePasswordRequest;
import com.svenruppert.urlshortener.core.auth.LoginRequest;
import com.svenruppert.urlshortener.core.auth.LoginResponse;
import com.svenruppert.urlshortener.core.auth.MeResponse;
import com.svenruppert.urlshortener.core.auth.SetupRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_LOGIN;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_LOGOUT;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_LOGOUT_ALL;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_ME;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_AUTH_ME_PASSWORD;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_BOOTSTRAP_STATUS;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_SETUP;

/**
 * Client for the authentication endpoints (PRE-REBUILD-SCOPE Kap. 2.6):
 * {@code POST /api/login}, {@code POST /api/logout}, {@code GET /api/me}.
 */
public class AuthClient
    implements HasLogger {

  private final String baseUrl;
  private final HttpClient http;

  public AuthClient() {
    this(ADMIN_SERVER_URL);
  }

  public AuthClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  /** Whether the first-admin bootstrap is still required (GET /api/bootstrap/status, public). */
  public Result<BootstrapStatusResponse, String> bootstrapStatus() {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH_BOOTSTRAP_STATUS))
        .GET()
        .build();
    return send(request, HttpStatus.OK, body -> JacksonJson.fromJson(body, BootstrapStatusResponse.class));
  }

  /**
   * Token-gated first-admin creation (POST /api/setup, public). Returns
   * success on HTTP 201; the failure message carries the server body so
   * the caller can humanise 401 (bad token) / 422 (password) / 409
   * (already initialised).
   */
  public Result<Boolean, String> setup(String token, String username, String password) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH_SETUP))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(
            JacksonJson.toJson(new SetupRequest(token, username, password))))
        .build();
    return send(request, HttpStatus.CREATED, body -> Boolean.TRUE);
  }

  /** Logs in and returns the Bearer token wrapped in a {@link LoginResponse}. */
  public Result<LoginResponse, String> login(String username, String password) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH_AUTH_LOGIN))
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(
            JacksonJson.toJson(new LoginRequest(username, password))))
        .build();
    return send(request, HttpStatus.OK, body -> JacksonJson.fromJson(body, LoginResponse.class));
  }

  /**
   * Convenience for CLI/tests: logs in and installs the token as the
   * process-wide default (see {@link AuthContext}).
   */
  public Result<LoginResponse, String> loginAndActivate(String username, String password) {
    return login(username, password)
        .peek(response -> AuthContext.setGlobalToken(response.token()));
  }

  /** Revokes the given token. Idempotent on the server side. */
  public Result<Boolean, String> logout(String token) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH_AUTH_LOGOUT))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    return send(request, HttpStatus.OK, body -> Boolean.TRUE);
  }

  /** Global logout: revokes every token of the current subject (POST /api/logout/all). */
  public Result<Boolean, String> logoutAll(String token) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH_AUTH_LOGOUT_ALL))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    return send(request, HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  /** Self-service password change (POST /api/me/password). */
  public Result<Boolean, String> changePassword(String token, String currentPassword, String newPassword) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH_AUTH_ME_PASSWORD))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(
            JacksonJson.toJson(new ChangePasswordRequest(currentPassword, newPassword))))
        .build();
    return send(request, HttpStatus.OK, body -> Boolean.TRUE);
  }

  /** Identity of the token's subject: roles + permissions. */
  public Result<MeResponse, String> me(String token) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH_AUTH_ME))
        .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
        .GET()
        .build();
    return send(request, HttpStatus.OK, body -> JacksonJson.fromJson(body, MeResponse.class));
  }

  private <T> Result<T, String> send(HttpRequest request, HttpStatus expected, Function<String, T> parser) {
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != expected.code()) {
        logger().warn("unexpected status {} from {}", response.statusCode(), request.uri());
        return Result.failure("Unexpected status " + response.statusCode() + ": " + response.body());
      }
      return Result.success(parser.apply(response.body()));
    } catch (IOException e) {
      logger().warn("I/O error calling {}: {}", request.uri(), e.getMessage());
      return Result.failure("I/O error calling " + request.uri() + ": " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.failure("Interrupted while calling " + request.uri());
    }
  }
}
