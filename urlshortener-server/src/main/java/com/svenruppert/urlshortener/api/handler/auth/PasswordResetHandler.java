package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.api.security.PasswordResetTokenStore;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.UserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.security.services.PasswordPreflight;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.SecurityProfile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.jsonMapOrBadRequest;
import static com.svenruppert.urlshortener.core.DefaultValues.PATH_PASSWORD_RESET;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * Public password-reset flow (P180):
 * <ul>
 *   <li>{@code POST /api/password-reset/request} {"username"} — always 202
 *       (no user enumeration); a valid user gets a single-use token handed to
 *       the delivery channel (a real deployment emails it). The token itself is
 *       never logged (SEC-H4). Under the development profile it is echoed as
 *       {@code devToken} for tests.</li>
 *   <li>{@code POST /api/password-reset/confirm} {"token","newPassword"} —
 *       consumes the token and sets the new password (length + compromised
 *       preflight enforced).</li>
 * </ul>
 */
public final class PasswordResetHandler
    implements HttpHandler, HasLogger {

  private static final String REQUEST = PATH_PASSWORD_RESET + "/request";
  private static final String CONFIRM = PATH_PASSWORD_RESET + "/confirm";
  private static final int MIN_PASSWORD_LENGTH = 8;

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePost(ex)) return;
    String path = ex.getRequestURI().getPath();
    if (REQUEST.equals(path)) {
      requestReset(ex);
      return;
    }
    if (CONFIRM.equals(path)) {
      confirmReset(ex);
      return;
    }
    ErrorResponses.notFound(ex, "unknown password-reset action");
  }

  private void requestReset(HttpExchange ex)
      throws IOException {
    Map<String, String> body = jsonMapOrBadRequest(ex);
    if (body == null) return;
    String username = body == null ? null : body.get("username");
    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("status", "accepted");

    if (!isNullOrBlank(username)) {
      Optional<ApiUser> user = UserDirectoryProvider.directory().findByUsername(username);
      if (user.isPresent()) {
        String token = PasswordResetTokenStore.INSTANCE.issue(user.get().id());
        // SEC-H4: the token is a credential — it must never reach the log.
        logger().info("password reset requested for '{}' — single-use token issued (not logged)", username);
        if (SecurityProfile.current() == SecurityProfile.DEVELOPMENT) {
          resp.put("devToken", token);
        }
      } else {
        logger().info("password reset requested for unknown user '{}' — ignored", username);
      }
    }
    // Always 202, whether or not the user exists (no enumeration).
    SuccessResponses.withStatusJson(ex, HttpStatus.ACCEPTED.code(), JacksonJson.toJson(resp));
  }

  private void confirmReset(HttpExchange ex)
      throws IOException {
    Map<String, String> body = jsonMapOrBadRequest(ex);
    if (body == null) return;
    String token = body == null ? null : body.get("token");
    String newPassword = body == null ? null : body.get("newPassword");
    if (isNullOrBlank(token) || isNullOrBlank(newPassword)) {
      ErrorResponses.badRequest(ex, "token and newPassword are required");
      return;
    }
    Optional<Long> userId = PasswordResetTokenStore.INSTANCE.consume(token);
    if (userId.isEmpty()) {
      ErrorResponses.withStatus(ex, HttpStatus.UNPROCESSABLE_CONTENT, "invalid or expired reset token");
      return;
    }
    UserDirectory directory = UserDirectoryProvider.directory();
    Optional<ApiUser> user = directory.findById(userId.get());
    if (user.isEmpty()) {
      ErrorResponses.withStatus(ex, HttpStatus.UNPROCESSABLE_CONTENT, "invalid or expired reset token");
      return;
    }
    if (newPassword.length() < MIN_PASSWORD_LENGTH) {
      ErrorResponses.invalidParameter(ex, "password_too_short");
      return;
    }
    if (!PasswordPreflight.isAcceptable(newPassword)) {
      ErrorResponses.invalidParameter(ex, "password is known to be compromised");
      return;
    }
    directory.updatePassword(userId.get(), newPassword);
    logger().info("password reset completed for user id={}", userId.get());
    SuccessResponses.okJson(ex, "{\"status\":\"reset\"}");
  }
}
