package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import eu.jsentinel.jcustos.authentication.AuthenticationService;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.bruteforce.LoginAttemptContext;
import eu.jsentinel.jcustos.bruteforce.LoginAttemptDecision;
import eu.jsentinel.jcustos.events.api.JCustosEventSeverity;
import com.svenruppert.urlshortener.api.security.AppPasswordHashing;
import com.svenruppert.urlshortener.api.security.events.SecurityEventBus;
import com.svenruppert.urlshortener.api.security.LoginAttemptGuard;
import com.svenruppert.urlshortener.api.security.TokenStore;
import com.svenruppert.urlshortener.api.security.services.VersionBumper;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.Credentials;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JsonUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.readBody;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * POST /api/login
 * Request JSON: { "username": "...", "password": "..." }
 * Responses:
 * - 200 OK, body: { "token": "<uuid>", "user": "<displayName>" }
 * - 400 Bad Request (missing fields)
 * - 401 Unauthorized (invalid credentials)
 * - 405 Method Not Allowed (others)
 */
public class LoginHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePost(ex)) return;
    try {
      Map<String, String> body = JsonUtils.parseJson(readBody(ex.getRequestBody()));
      String username = body.get("username");
      String password = body.get("password");
      if (isNullOrBlank(username) || isNullOrBlank(password)) {
        ErrorResponses.badRequest(ex, "Missing 'username' or 'password'");
        return;
      }

      // Brute-force lockout (P110): reject early if this (user, IP) is locked.
      LoginAttemptContext attempt = LoginAttemptContext.now(username, clientAddress(ex), null);
      LoginAttemptDecision decision = LoginAttemptGuard.INSTANCE.beforeAttempt(attempt);
      if (decision instanceof LoginAttemptDecision.LockedOut lockedOut) {
        long retryAfter = lockedOut.remaining().toSeconds();
        ex.getResponseHeaders().add("Retry-After", String.valueOf(retryAfter));
        logger().info("login locked out for '{}' ({} failures) - retry in {}s",
            username, lockedOut.failedAttempts(), retryAfter);
        ErrorResponses.withStatus(ex, HttpStatus.TOO_MANY_REQUESTS,
            "too many failed login attempts; retry in " + retryAfter + "s");
        return;
      }

      Credentials credentials = new Credentials(username, password);
      AuthenticationService<Credentials, ApiUser> authn =
          JCustosServiceResolver.authenticationService();
      if (!authn.checkCredentials(credentials)) {
        logger().info("login rejected for username '{}'", username);
        // Timing-safe: equalise cost so unknown users can't be enumerated
        // via response time (P140).
        AppPasswordHashing.dummyVerify(password.toCharArray());
        LoginAttemptGuard.INSTANCE.recordFailure(attempt);
        SecurityEventBus.instance().publishSecurity(
            "login.failure", username, "invalid credentials", JCustosEventSeverity.WARNING);
        ErrorResponses.unauthorized(ex);
        return;
      }

      Optional<ApiUser> user = UserDirectoryProvider.directory().findByCredentials(credentials);
      if (user.isEmpty()) {
        LoginAttemptGuard.INSTANCE.recordFailure(attempt);
        ErrorResponses.unauthorized(ex);
        return;
      }

      LoginAttemptGuard.INSTANCE.recordSuccess(attempt);
      // Capture the subject's current version so a later role change
      // (which bumps it) drifts this token on the next request.
      String token = TokenStore.INSTANCE.issue(user.get(), VersionBumper.current(user.get()));
      logger().info("login succeeded for username '{}'", username);
      SecurityEventBus.instance().publishSecurity(
          "login.success", username, "bearer token issued", JCustosEventSeverity.NOTICE);
      Map<String, String> response = new LinkedHashMap<>();
      response.put("token", token);
      response.put("user", user.get().name());
      SuccessResponses.okJson(ex, JsonUtils.toJson(response));
    } catch (RuntimeException e) {
      logger().warn("login failed - {}", e.toString());
      ErrorResponses.internalServerError(ex, "internal_error");
    }
  }

  private static String clientAddress(HttpExchange ex) {
    String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
    var remote = ex.getRemoteAddress();
    return remote == null || remote.getAddress() == null
        ? "unknown" : remote.getAddress().getHostAddress();
  }
}
