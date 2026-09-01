package com.svenruppert.urlshortener.api.security.bootstrap;

import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.authorization.api.SubjectIdResolver;
import eu.jsentinel.jcustos.dx.bootstrap.SessionBootstrap;
import eu.jsentinel.jcustos.session.JCustosVersionStore;

import java.util.Optional;

/**
 * Hardening-layer extension (order 20): wires the drift-detection
 * infrastructure onto the bootstrap chain — the per-subject version
 * store and the {@link SubjectIdResolver}.
 *
 * <p>Argon2id is NOT configured here: the single
 * {@code AppPasswordHashing.SERVICE} the whole app hashes/verifies
 * with is already Argon2id (Stage F), and {@code DefaultBootstrapExtension}
 * feeds that same instance into the credential subsystem. Re-setting
 * it here would risk a second, cross-incompatible instance.
 */
public final class HardeningBootstrapExtension implements BootstrapExtension {

  @Override
  public void contributeSessions(SessionBootstrap s) {
    Optional<JCustosVersionStore> versionStore =
        JCustosServiceResolver.findJCustosVersionStore();
    Optional<SubjectIdResolver<Object>> resolver =
        JCustosServiceResolver.findSubjectIdResolver();
    versionStore.ifPresent(s::securityVersion);
    resolver.ifPresent(s::subjectIdResolver);
  }

  @Override
  public int order() {
    return 20;
  }
}
