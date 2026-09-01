package com.svenruppert.urlshortener.api.security.services;

import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.authorization.api.tenant.TenantId;
import eu.jsentinel.jcustos.logout.SubjectId;
import eu.jsentinel.jcustos.session.JCustosVersionKey;
import eu.jsentinel.jcustos.session.JCustosVersionStore;
import com.svenruppert.urlshortener.api.security.model.ApiUser;

import java.util.Optional;

/**
 * Increments the per-subject {@code JCustosVersion} so any Bearer
 * token captured before the bump drifts on its next request and the
 * affected user must re-authenticate. Call after every role-mutating
 * operation (assign / revoke role, delete user) — those endpoints
 * arrive with the user-management handlers in Stage G.
 *
 * <p>No-op when the {@link JCustosVersionStore} SPI is absent, so
 * call sites can stay unconditional.
 */
public final class VersionBumper {

  private VersionBumper() {
  }

  public static Optional<Long> bump(ApiUser user) {
    if (user == null) {
      return Optional.empty();
    }
    Optional<JCustosVersionStore> storeOpt =
        JCustosServiceResolver.findJCustosVersionStore();
    if (storeOpt.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(storeOpt.get().increment(keyFor(user)).value());
  }

  /**
   * Current per-subject version, captured into the Bearer token at
   * login. Returns 0 when the version-store SPI is absent, so drift
   * detection is a consistent no-op in that case.
   */
  public static long current(ApiUser user) {
    if (user == null) {
      return 0L;
    }
    return JCustosServiceResolver.findJCustosVersionStore()
        .map(store -> store.current(keyFor(user)).value())
        .orElse(0L);
  }

  private static JCustosVersionKey keyFor(ApiUser user) {
    return new JCustosVersionKey(TenantId.DEFAULT, SubjectId.of(user.id().toString()));
  }
}
