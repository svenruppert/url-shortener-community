package com.svenruppert.urlshortener.api.security.bootstrap;

import eu.jsentinel.jcustos.dx.bootstrap.AuditBootstrap;
import eu.jsentinel.jcustos.dx.bootstrap.CredentialBootstrap;
import com.svenruppert.urlshortener.api.security.AppPasswordHashing;

/**
 * Stage-B defaults: in-memory ring buffer for audit, PBKDF2 for
 * password hashing. Registered via
 * {@code META-INF/services/com.svenruppert.urlshortener.api.security.bootstrap.BootstrapExtension}.
 * {@code order() == 0} so persistence/hardening extensions can stack
 * on top later.
 */
public final class DefaultBootstrapExtension implements BootstrapExtension {

  @Override
  public void contributeAudit(AuditBootstrap a) {
    a.ringBuffer(256).logging();
  }

  @Override
  public void contributeCredentials(CredentialBootstrap c) {
    // Same instance the user directories hash/verify with, so the
    // framework's credential subsystem stays consistent with logins.
    c.hashing(AppPasswordHashing.SERVICE);
  }

  @Override
  public int order() {
    return 0;
  }
}
