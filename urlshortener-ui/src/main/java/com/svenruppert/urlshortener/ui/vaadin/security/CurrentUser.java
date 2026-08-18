package com.svenruppert.urlshortener.ui.vaadin.security;

import com.svenruppert.jsentinel.authorization.api.SubjectStores;

/**
 * Session-scoped subject shortcuts for view code. UI-side visibility only —
 * every operation is enforced again server-side (permissions + OwnerGuard).
 */
public final class CurrentUser {

  private CurrentUser() {
  }

  /** @return true if the signed-in user carries the ADMIN role. */
  public static boolean isAdmin() {
    return SubjectStores.subjectStore().currentSubject(AppUser.class)
        .map(user -> user.roles().contains(AppRole.ADMIN))
        .orElse(false);
  }
}
