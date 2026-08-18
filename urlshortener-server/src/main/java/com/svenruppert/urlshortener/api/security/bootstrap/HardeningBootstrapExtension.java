package com.svenruppert.urlshortener.api.security.bootstrap;

import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.authorization.api.SubjectIdResolver;
import com.svenruppert.jsentinel.dx.bootstrap.SessionBootstrap;
import com.svenruppert.jsentinel.session.JSentinelVersionStore;

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
    Optional<JSentinelVersionStore> versionStore =
        JSentinelServiceResolver.findJSentinelVersionStore();
    Optional<SubjectIdResolver<Object>> resolver =
        JSentinelServiceResolver.findSubjectIdResolver();
    versionStore.ifPresent(s::securityVersion);
    resolver.ifPresent(s::subjectIdResolver);
  }

  @Override
  public int order() {
    return 20;
  }
}
