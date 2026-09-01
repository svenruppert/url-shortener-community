package com.svenruppert.urlshortener.api.security.bootstrap;

import eu.jsentinel.jcustos.dx.bootstrap.AuditBootstrap;
import eu.jsentinel.jcustos.dx.bootstrap.CredentialBootstrap;
import eu.jsentinel.jcustos.dx.bootstrap.SessionBootstrap;

/**
 * Project-local SPI for additive bootstrap configuration on the
 * server side. Stage B defines the contract; later stages
 * (persistence, hardening) each ship one implementation registered
 * via {@code META-INF/services/{@value #SERVICE_NAME}}.
 *
 * <p>{@link BootstrapBuilder#apply} loads every registered
 * implementation through {@link java.util.ServiceLoader}, sorts by
 * {@link #order()}, and invokes the three {@code contribute*} hooks
 * inside a single {@code .audit(...)} / {@code .sessions(...)} /
 * {@code .credentials(...)} call on the fluent
 * {@code RestSecurity.bootstrap()} chain — so multiple layers can
 * configure the same sub-aspect without overwriting each other.
 */
public interface BootstrapExtension {

  /** The SPI service-file name for this interface. */
  String SERVICE_NAME = "com.svenruppert.urlshortener.api.security.bootstrap.BootstrapExtension";

  /** Adds audit-related configuration. */
  default void contributeAudit(AuditBootstrap a) {
  }

  /** Adds session-related configuration. */
  default void contributeSessions(SessionBootstrap s) {
  }

  /** Adds credential / hashing configuration. */
  default void contributeCredentials(CredentialBootstrap c) {
  }

  /**
   * Order in which extensions are invoked. Lower runs first.
   * Stage B defaults: 0. Persistence: 10. Hardening: 20.
   */
  default int order() {
    return 0;
  }
}
