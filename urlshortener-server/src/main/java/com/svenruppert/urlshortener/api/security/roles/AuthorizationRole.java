package com.svenruppert.urlshortener.api.security.roles;

/**
 * Role catalog of the REST API.
 *
 * <p>The role-to-permission table lives in
 * {@code ApiAuthorizationService}. Add a new role here and map it to
 * permissions there.
 */
public enum AuthorizationRole {
  ADMIN,
  USER
}
