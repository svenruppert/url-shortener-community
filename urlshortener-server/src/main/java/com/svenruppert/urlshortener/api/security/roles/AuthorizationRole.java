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
  USER,
  /**
   * Read-only analyst on assigned campaigns (V00.19.00 P050): may open the
   * aggregated reports, but only over the links of the campaigns assigned to
   * them. Holds no manage permission and no {@code link:read:all}, which is
   * exactly what makes the row-level scoping bite.
   */
  CAMPAIGN_ANALYST
}
