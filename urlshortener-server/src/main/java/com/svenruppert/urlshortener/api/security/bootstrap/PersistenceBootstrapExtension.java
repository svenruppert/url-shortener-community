package com.svenruppert.urlshortener.api.security.bootstrap;

import eu.jsentinel.jcustos.dx.bootstrap.AuditBootstrap;
import eu.jsentinel.jcustos.dx.bootstrap.SessionBootstrap;

/**
 * Persistence-layer extension (order 10): contributes the
 * Eclipse-Store-backed audit + session stores to the bootstrap chain
 * so both survive a restart.
 *
 * <p>Registered via
 * {@code META-INF/services/com.svenruppert.urlshortener.api.security.bootstrap.BootstrapExtension}.
 * The {@code contribute*} hooks run inside {@code RestSecurity.bootstrap()
 * .install()} and pull the storage lazily — no eager static
 * initialiser, so merely class-loading this extension (ServiceLoader
 * enumeration, static analysis, a PIT worker) does NOT open
 * Eclipse-Store before the credentials sub-builder configured the
 * hashing service.
 */
public final class PersistenceBootstrapExtension implements BootstrapExtension {

  @Override
  public void contributeAudit(AuditBootstrap a) {
    a.storeBacked(JCustosStorageProvider.framework().auditEventStore()).logging();
  }

  @Override
  public void contributeSessions(SessionBootstrap s) {
    s.storeBacked(JCustosStorageProvider.framework().sessionStore());
  }

  @Override
  public int order() {
    return 10;
  }
}
