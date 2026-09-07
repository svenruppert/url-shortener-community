package com.svenruppert.urlshortener.api.handler.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.api.JCustosSubject;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.urlshortener.api.security.PasswordHistoryGuard;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.Credentials;
import com.svenruppert.urlshortener.api.security.model.UserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.security.services.PasswordPreflight;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.api.utils.SuccessResponses;
import com.svenruppert.urlshortener.core.auth.ChangePasswordRequest;

import java.io.IOException;
import java.util.Optional;

import static com.svenruppert.urlshortener.api.utils.RequestBodyUtils.jsonBodyOrBadRequest;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * POST /api/me/password — self-service password change
 * (PRE-REBUILD-SCOPE Kap. 2.6). Authenticated-only. Verifies the
 * current password, rejects compromised new passwords, then updates.
 */
public class MePasswordHandler
    implements HttpHandler, HasLogger {

  @Override
  public void handle(HttpExchange ex)
      throws IOException {
    if (!RequestMethodUtils.requirePost(ex)) return;
    Optional<JCustosSubject> subject = CurrentSubject.of(ex);
    if (subject.isEmpty()) {
      ErrorResponses.unauthorized(ex);
      return;
    }
    try {
      ChangePasswordRequest req = jsonBodyOrBadRequest(ex, ChangePasswordRequest.class);
      if (req == null) return;
      if (req == null || isNullOrBlank(req.currentPassword()) || isNullOrBlank(req.newPassword())) {
        ErrorResponses.badRequest(ex, "currentPassword and newPassword are required");
        return;
      }

      Long id = parseId(subject.get().subjectId());
      UserDirectory directory = UserDirectoryProvider.directory();
      Optional<ApiUser> user = id == null ? Optional.empty() : directory.findById(id);
      if (user.isEmpty()) {
        ErrorResponses.unauthorized(ex);
        return;
      }

      // Re-authenticate with the current password.
      boolean ok = directory.findByCredentials(
          new Credentials(user.get().name(), req.currentPassword())).isPresent();
      if (!ok) {
        ErrorResponses.badRequest(ex, "current password incorrect");
        return;
      }
      if (req.newPassword().length() < 8) {
        ErrorResponses.invalidParameter(ex, "password_too_short");
        return;
      }
      if (!PasswordPreflight.isAcceptable(req.newPassword())) {
        ErrorResponses.invalidParameter(ex, "password is known to be compromised");
        return;
      }

      String uid = String.valueOf(id);
      if (PasswordHistoryGuard.INSTANCE.isReuse(uid, req.newPassword())) {
        ErrorResponses.invalidParameter(ex, "password_recently_used");
        return;
      }
      directory.updatePassword(id, req.newPassword());
      PasswordHistoryGuard.INSTANCE.record(uid, req.newPassword());
      logger().info("password changed for user id={}", id);
      SuccessResponses.okJson(ex, "{\"ok\":true}");
    } catch (RuntimeException e) {
      logger().warn("password change failed - {}", e.toString());
      ErrorResponses.internalServerError(ex, "internal_error");
    }
  }

  private static Long parseId(String s) {
    try {
      return Long.valueOf(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
