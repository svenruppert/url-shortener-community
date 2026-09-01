package com.svenruppert.urlshortener.api.security.oidc;

import com.svenruppert.functional.result.Result;
import eu.jsentinel.jcustos.authorization.api.JCustosSubject;
import eu.jsentinel.jcustos.identity.oidc.DefaultClaimsToSubjectMapper;
import eu.jsentinel.jcustos.identity.oidc.DefaultIdTokenValidator;
import eu.jsentinel.jcustos.identity.oidc.EmptyPermissionsMapper;
import eu.jsentinel.jcustos.jwt.api.AlgorithmAllowList;
import eu.jsentinel.jcustos.jwt.api.ClaimExpectations;
import eu.jsentinel.jcustos.jwt.api.ClockSkewPolicy;
import eu.jsentinel.jcustos.jwt.api.JwsAlgorithm;
import eu.jsentinel.jcustos.jwt.api.JwtValidator;
import eu.jsentinel.jcustos.jwt.api.JwtValidatorSpec;
import eu.jsentinel.jcustos.jwt.impl.NimbusJwtValidatorFactory;
import eu.jsentinel.jcustos.oidc.api.ClaimsToSubjectMapper;
import eu.jsentinel.jcustos.oidc.api.IdTokenExpectations;
import eu.jsentinel.jcustos.oidc.api.IdTokenValidationError;
import eu.jsentinel.jcustos.oidc.api.IdTokenValidator;

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
 *   <li>maps the resulting claims onto a {@link JCustosSubject} — the same
 *       subject type the rest of the security stack consumes — with roles taken
 *       from the {@code roles} claim ({@link OidcRolesMapper}).</li>
 * </ol>
 *
 * <p>This is the tested core of SSO. The browser-facing Authorization-Code +
 * PKCE redirect flow, Vaadin login buttons and vendor presets
 * ({@code jCustos-oauth2}/{@code -identity-vendor-*}) are carry-over and
 * require real IdP client credentials; this bridge is exercised end-to-end
 * against the {@code jCustos-test-oidc} {@code StubIdentityProvider}.
 */
public final class OidcSubjectBridge {

  private OidcSubjectBridge() {
  }

  public static Result<JCustosSubject, IdTokenValidationError> authenticate(
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
