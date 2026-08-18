package com.svenruppert.urlshortener.api.security.oidc;

import com.svenruppert.jsentinel.authorization.api.roles.RoleName;
import com.svenruppert.jsentinel.oidc.api.ClaimsToRolesMapper;
import com.svenruppert.jsentinel.oidc.api.UserInfoResponse;
import com.svenruppert.jsentinel.oidc.api.ValidatedIdToken;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps an external IdP's {@code roles} claim onto the application's
 * {@link RoleName} model (P270), so a Google/GitHub/Keycloak login feeds the
 * same authorization flow as a local login. The claim may be a JSON array or a
 * space/comma-separated string; anything else yields no roles.
 */
public final class OidcRolesMapper
    implements ClaimsToRolesMapper {

  /** ID-token claim carrying the IdP-side roles. */
  public static final String ROLES_CLAIM = "roles";

  @Override
  public Set<RoleName> mapRoles(ValidatedIdToken idToken, Optional<UserInfoResponse> userInfo) {
    return toRoleNames(idToken.jwt().claims().get(ROLES_CLAIM));
  }

  private static Set<RoleName> toRoleNames(Object raw) {
    if (raw instanceof Collection<?> values) {
      return values.stream()
          .filter(Objects::nonNull)
          .map(Object::toString)
          .filter(s -> !s.isBlank())
          .map(RoleName::new)
          .collect(Collectors.toUnmodifiableSet());
    }
    if (raw instanceof String s && !s.isBlank()) {
      return Arrays.stream(s.split("[ ,]+"))
          .filter(v -> !v.isBlank())
          .map(RoleName::new)
          .collect(Collectors.toUnmodifiableSet());
    }
    return Set.of();
  }
}
