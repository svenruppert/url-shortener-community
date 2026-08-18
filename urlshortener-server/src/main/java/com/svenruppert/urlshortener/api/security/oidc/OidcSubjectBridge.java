package com.svenruppert.urlshortener.api.security.oidc;

import com.svenruppert.functional.result.Result;
import com.svenruppert.jsentinel.authorization.api.JSentinelSubject;
import com.svenruppert.jsentinel.identity.oidc.DefaultClaimsToSubjectMapper;
import com.svenruppert.jsentinel.identity.oidc.DefaultIdTokenValidator;
import com.svenruppert.jsentinel.identity.oidc.EmptyPermissionsMapper;
import com.svenruppert.jsentinel.jwt.api.AlgorithmAllowList;
import com.svenruppert.jsentinel.jwt.api.ClaimExpectations;
import com.svenruppert.jsentinel.jwt.api.ClockSkewPolicy;
import com.svenruppert.jsentinel.jwt.api.JwsAlgorithm;
import com.svenruppert.jsentinel.jwt.api.JwtValidator;
import com.svenruppert.jsentinel.jwt.api.JwtValidatorSpec;
import com.svenruppert.jsentinel.jwt.impl.NimbusJwtValidatorFactory;
import com.svenruppert.jsentinel.oidc.api.ClaimsToSubjectMapper;
import com.svenruppert.jsentinel.oidc.api.IdTokenExpectations;
import com.svenruppert.jsentinel.oidc.api.IdTokenValidationError;
import com.svenruppert.jsentinel.oidc.api.IdTokenValidator;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * Bridges an external OIDC provider to this application's subject/role flow
 * (P270). Given a raw ID token plus the provider's JWKS URI, issuer and
 * client-id (audience), it:
 * <ol>
 *   <li>validates the token signature against the provider JWKS (RS256) and the
 *       standard claim set via {@link DefaultIdTokenValidator};</li>
 *   <li>maps the resulting claims onto a {@link JSentinelSubject} — the same
 *       subject type the rest of the security stack consumes — with roles taken
 *       from the {@code roles} claim ({@link OidcRolesMapper}).</li>
 * </ol>
 *
 * <p>This is the tested core of SSO. The browser-facing Authorization-Code +
 * PKCE redirect flow, Vaadin login buttons and vendor presets
 * ({@code jSentinel-oauth2}/{@code -identity-vendor-*}) are carry-over and
 * require real IdP client credentials; this bridge is exercised end-to-end
 * against the {@code jSentinel-test-oidc} {@code StubIdentityProvider}.
 */
public final class OidcSubjectBridge {

  private OidcSubjectBridge() {
  }

  public static Result<JSentinelSubject, IdTokenValidationError> authenticate(
      String idToken, URI jwksUri, String issuer, String audience) {

    JwtValidatorSpec spec = new JwtValidatorSpec(
        jwksUri,
        new AlgorithmAllowList(Set.of(JwsAlgorithm.RS256)),
        new ClaimExpectations(Optional.empty(), Set.of(), true, false, false, false,
            ClockSkewPolicy.DEFAULT));
    JwtValidator jwtValidator = new NimbusJwtValidatorFactory().create(spec);
    IdTokenValidator validator = new DefaultIdTokenValidator(jwtValidator);
    ClaimsToSubjectMapper subjectMapper =
        new DefaultClaimsToSubjectMapper(new OidcRolesMapper(), EmptyPermissionsMapper.INSTANCE);
    IdTokenExpectations expectations =
        IdTokenExpectations.of(issuer, audience, Optional.empty());

    return validator.validate(idToken, expectations)
        .map(validated -> subjectMapper.map(validated, Optional.empty()));
  }
}
