package com.svenruppert.urlshortener.api.security;

import com.sun.net.httpserver.HttpExchange;
import eu.jsentinel.jcustos.authorization.api.JCustosSubject;
import eu.jsentinel.jcustos.authorization.api.permissions.PermissionName;

import java.util.Optional;
import java.util.Set;

/**
 * Link-owner enforcement (P210, hardened in V00.17.00 P030). A short code may
 * be managed by its owner, or by any subject holding the {@code link:read:all}
 * permission (admins). Unowned codes are manageable by admins only — the
 * pre-00.17 fail-open branch ("unowned ⇒ anyone") is gone: ownership is now
 * durable and the P020 backfill assigned every legacy code to the first admin,
 * so a missing owner record is an anomaly, not a compatibility case.
 */
public final class OwnerGuard {

  private static final PermissionName READ_ALL = new PermissionName("link:read:all");

  private OwnerGuard() {
  }

  /** @return true if the current subject may manage {@code shortCode}. */
  public static boolean canManage(HttpExchange exchange, String shortCode) {
    Optional<JCustosSubject> subject = CurrentSubject.of(exchange);
    if (subject.isEmpty()) {
      return false;
    }
    if (subject.get().permissionNames().contains(READ_ALL)) {
      return true; // admins bypass
    }
    Optional<Long> owner = LinkOwnershipStore.INSTANCE.owner(shortCode);
    if (owner.isEmpty()) {
      return false; // unowned ⇒ admin-only (fail-closed since P030)
    }
    Long uid = currentUserId(exchange);
    return uid != null && owner.get().equals(uid);
  }

  /** @return true if the current subject holds {@code link:read:all} (admins). */
  public static boolean canReadAll(HttpExchange exchange) {
    return CurrentSubject.of(exchange)
        .map(s -> s.permissionNames().contains(READ_ALL))
        .orElse(false);
  }

  /**
   * The read-scoping set for the current subject (V00.17.00 P050):
   * {@code null} means "no restriction" (admin with {@code link:read:all});
   * otherwise the normalized codes the caller owns — possibly empty, which
   * matches nothing.
   */
  public static Set<String> readRestriction(HttpExchange exchange) {
    if (canReadAll(exchange)) {
      return null;
    }
    return LinkOwnershipStore.INSTANCE.codesOwnedBy(currentUserId(exchange));
  }

  /** The numeric user id of the current subject, or {@code null}. */
  public static Long currentUserId(HttpExchange exchange) {
    return CurrentSubject.of(exchange)
        .map(JCustosSubject::subjectId)
        .map(OwnerGuard::parseId)
        .orElse(null);
  }

  private static Long parseId(String s) {
    try {
      return s == null ? null : Long.valueOf(s.trim());
    } catch (NumberFormatException notNumeric) {
      return null;
    }
  }
}
