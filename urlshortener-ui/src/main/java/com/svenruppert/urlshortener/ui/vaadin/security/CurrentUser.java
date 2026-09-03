package com.svenruppert.urlshortener.ui.vaadin.security;

import eu.jsentinel.jcustos.authorization.api.SubjectStores;
import java.util.Arrays;
import java.util.Collection;

/**
 * Session-scoped subject shortcuts for view code. UI-side visibility only —
 * every operation is enforced again server-side (permissions + OwnerGuard).
 */
public final class CurrentUser {

  private CurrentUser() {
  }

  /** @return true if the signed-in user carries the ADMIN role. */
  public static boolean isAdmin() {
    return hasAnyRole(AppRole.ADMIN);
  }

  /** @return true if the signed-in user carries at least one of {@code roles}. */
  public static boolean hasAnyRole(AppRole... roles) {
    if (roles == null || roles.length == 0) return false;
    return SubjectStores.subjectStore().currentSubject(AppUser.class)
        .map(user -> Arrays.stream(roles).anyMatch(user.roles()::contains))
        .orElse(false);
  }

  /** @return true if the signed-in user carries at least one of {@code roles}. */
  public static boolean hasAnyRole(Collection<AppRole> roles) {
    if (roles == null || roles.isEmpty()) return false;
    return hasAnyRole(roles.toArray(new AppRole[0]));
  }
}
