package com.svenruppert.urlshortener.api.security;

import eu.jsentinel.jcustos.authorization.api.AuthorizationService;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.authorization.api.JCustosSubject;
import eu.jsentinel.jcustos.authorization.api.permissions.PermissionName;
import eu.jsentinel.jcustos.authorization.api.roles.RoleName;
import eu.jsentinel.jcustos.rest.BearerTokenExtractor;
import eu.jsentinel.jcustos.rest.RestRequest;
import eu.jsentinel.jcustos.rest.RestSubjectResolver;
import com.svenruppert.urlshortener.api.security.model.ApiUser;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the current {@link JCustosSubject} from a Bearer token.
 * Roles + permissions for the subject come from the
 * {@link AuthorizationService}.
 */
public final class ApiRestSubjectResolver implements RestSubjectResolver {

  private static final BearerTokenExtractor BEARER = new BearerTokenExtractor();

  @Override
  public Optional<JCustosSubject> resolveSubject(RestRequest request) {
    Optional<String> token = BEARER.extract(request);
    if (token.isEmpty()) return Optional.empty();
    return TokenStore.INSTANCE.resolve(token.get()).map(ApiRestSubjectResolver::toSubject);
  }

  static JCustosSubject toSubject(ApiUser user) {
    AuthorizationService<ApiUser> authz =
        JCustosServiceResolver.authorizationService();
    Set<RoleName> roles = authz.rolesFor(user).roleNames().stream()
        .collect(Collectors.toUnmodifiableSet());
    Set<PermissionName> perms = authz.permissionsFor(user).permissionNames().stream()
        .collect(Collectors.toUnmodifiableSet());
    return new JCustosSubject(
        String.valueOf(user.id()),
        user.name(),
        roles,
        perms);
  }
}
