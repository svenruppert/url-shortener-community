package com.svenruppert.urlshortener.api.security.services;

import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.audit.JCustosAuditService;
import eu.jsentinel.jcustos.audit.LoginSucceeded;
import eu.jsentinel.jcustos.authentication.AuthenticationService;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.autoservice.api.JCustosAutoService;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.Credentials;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;

import java.time.Clock;
import java.time.Instant;

/**
 * REST-side AuthenticationService. SPI-registered via
 * {@link JCustosAutoService @JCustosAutoService} — the annotation
 * processor produces the matching META-INF/services entry.
 */
@JCustosAutoService(AuthenticationService.class)
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
    JCustosAuditService sink = JCustosServiceResolver.securityAuditService();
    try {
      sink.publish(new LoginSucceeded(
          Instant.now(Clock.systemUTC()), username, null, null));
    } catch (RuntimeException ignored) {
      // never block a successful login because the audit sink failed
    }
  }
}
