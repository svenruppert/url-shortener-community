package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.auth.CreateUserRequest;
import com.svenruppert.urlshortener.core.auth.UserSummary;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;
import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;
import static com.svenruppert.urlshortener.core.DefaultValues.CONTENT_TYPE;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_USERS;

/**
 * Client for the admin user-management endpoints (PRE-REBUILD-SCOPE
 * Kap. 2.7): list / create / delete users and assign / revoke roles.
 */
public class UsersClient
    implements HasLogger {

  private final String baseUrl;
  private final HttpClient http;
  private Supplier<String> bearerToken = AuthContext::getGlobalToken;

  public UsersClient() {
    this(ADMIN_SERVER_URL);
  }

  public UsersClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public UsersClient withBearerToken(Supplier<String> tokenSupplier) {
    this.bearerToken = tokenSupplier;
    return this;
  }

  public Result<List<UserSummary>, String> list() {
    return send(get(PATH_ADMIN_USERS), HttpStatus.OK,
        body -> List.of(JacksonJson.fromJson(body, UserSummary[].class)));
  }

  public Result<UserSummary, String> create(String username, String password, List<String> roles) {
    var request = post(PATH_ADMIN_USERS,
        JacksonJson.toJson(new CreateUserRequest(username, password, roles)));
    return send(request, HttpStatus.CREATED, body -> JacksonJson.fromJson(body, UserSummary.class));
  }

  public Result<Boolean, String> delete(long id) {
    var request = builder(PATH_ADMIN_USERS + "/" + id).DELETE().build();
    return send(request, HttpStatus.NO_CONTENT, body -> Boolean.TRUE);
  }

  public Result<UserSummary, String> assignRole(long id, String role) {
    var request = builder(PATH_ADMIN_USERS + "/" + id + "/roles/" + role)
        .POST(HttpRequest.BodyPublishers.noBody()).build();
    return send(request, HttpStatus.OK, body -> JacksonJson.fromJson(body, UserSummary.class));
  }

  public Result<UserSummary, String> revokeRole(long id, String role) {
    var request = builder(PATH_ADMIN_USERS + "/" + id + "/roles/" + role).DELETE().build();
    return send(request, HttpStatus.OK, body -> JacksonJson.fromJson(body, UserSummary.class));
  }

  private HttpRequest get(String path) {
    return builder(path).GET().build();
  }

  private HttpRequest post(String path, String json) {
    return builder(path)
        .header(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8())
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
  }

  private HttpRequest.Builder builder(String path) {
    var b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10));
    var token = bearerToken.get();
    if (token != null && !token.isBlank()) {
      b.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
    }
    return b;
  }

  private <T> Result<T, String> send(HttpRequest request, HttpStatus expected,
                                     java.util.function.Function<String, T> parser) {
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
