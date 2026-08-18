package com.svenruppert.urlshortener.core.auth;

import java.util.List;

/** Response of {@code GET /api/me} — identity of the authenticated caller. */
public record MeResponse(String id, String displayName, List<String> roles, List<String> permissions) {

  public boolean hasRole(String role) {
    return roles != null && roles.contains(role);
  }

  public boolean hasPermission(String permission) {
    return permissions != null && permissions.contains(permission);
  }
}
