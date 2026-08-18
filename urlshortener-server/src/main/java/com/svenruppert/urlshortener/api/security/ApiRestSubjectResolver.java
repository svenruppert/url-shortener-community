package com.svenruppert.urlshortener.api.security;

import com.svenruppert.jsentinel.authorization.api.AuthorizationService;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.authorization.api.JSentinelSubject;
import com.svenruppert.jsentinel.authorization.api.permissions.PermissionName;
import com.svenruppert.jsentinel.authorization.api.roles.RoleName;
import com.svenruppert.jsentinel.rest.BearerTokenExtractor;
import com.svenruppert.jsentinel.rest.RestRequest;
import com.svenruppert.jsentinel.rest.RestSubjectResolver;
import com.svenruppert.urlshortener.api.security.model.ApiUser;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the current {@link JSentinelSubject} from a Bearer token.
 * Roles + permissions for the subject come from the
 * {@link AuthorizationService}.
 */
public final class ApiRestSubjectResolver implements RestSubjectResolver {

  private static final BearerTokenExtractor BEARER = new BearerTokenExtractor();

  @Override
  public Optional<JSentinelSubject> resolveSubject(RestRequest request) {
    Optional<String> token = BEARER.extract(request);
    if (token.isEmpty()) return Optional.empty();
    return TokenStore.INSTANCE.resolve(token.get()).map(ApiRestSubjectResolver::toSubject);
  }

  static JSentinelSubject toSubject(ApiUser user) {
    AuthorizationService<ApiUser> authz =
        JSentinelServiceResolver.authorizationService();
    Set<RoleName> roles = authz.rolesFor(user).roleNames().stream()
        .collect(Collectors.toUnmodifiableSet());
    Set<PermissionName> perms = authz.permissionsFor(user).permissionNames().stream()
        .collect(Collectors.toUnmodifiableSet());
    return new JSentinelSubject(
        String.valueOf(user.id()),
        user.name(),
        roles,
        perms);
  }
}
