package com.svenruppert.urlshortener.api.security.model;

import com.svenruppert.urlshortener.api.security.roles.AuthorizationRole;

import java.util.Set;

/**
 * Authenticated subject of the REST API. Deliberately separate from
 * the UI-side subject type — REST has its own view of the user; the
 * AuthenticationService is the boundary.
 *
 * <p>The compact constructor defensively copies {@code roles} via
 * {@link Set#copyOf(java.util.Collection)} so the field always holds
 * an immutable set — the form Eclipse-Store can re-hydrate safely
 * (storing an {@code EnumSet} directly corrupts on reload).
 */
public record ApiUser(Long id, String name, Set<AuthorizationRole> roles) {
  public ApiUser {
    roles = Set.copyOf(roles);
  }
}
