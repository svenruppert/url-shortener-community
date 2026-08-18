package com.svenruppert.urlshortener.api.security.services;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.audit.JSentinelAuditService;
import com.svenruppert.jsentinel.audit.LoginSucceeded;
import com.svenruppert.jsentinel.authentication.AuthenticationService;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.autoservice.api.JSentinelAutoService;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.Credentials;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;

import java.time.Clock;
import java.time.Instant;

/**
 * REST-side AuthenticationService. SPI-registered via
 * {@link JSentinelAutoService @JSentinelAutoService} — the annotation
 * processor produces the matching META-INF/services entry.
 */
@JSentinelAutoService(AuthenticationService.class)
public class ApiAuthenticationService
    implements AuthenticationService<Credentials, ApiUser>, HasLogger {

  @Override
  public boolean checkCredentials(Credentials credentials) {
    if (credentials == null) {
      return false;
    }
    boolean ok = UserDirectoryProvider.directory().checkCredentials(credentials);
    if (ok) {
      auditLoginSucceeded(credentials.username());
    }
    return ok;
  }

  @Override
  public ApiUser loadSubject(Credentials credentials) {
    return UserDirectoryProvider.directory().findByCredentials(credentials).orElse(null);
  }

  @Override
  public Class<ApiUser> subjectType() {
    return ApiUser.class;
  }

  private static void auditLoginSucceeded(String username) {
    JSentinelAuditService sink = JSentinelServiceResolver.securityAuditService();
    try {
      sink.publish(new LoginSucceeded(
          Instant.now(Clock.systemUTC()), username, null, null));
    } catch (RuntimeException ignored) {
      // never block a successful login because the audit sink failed
    }
  }
}
