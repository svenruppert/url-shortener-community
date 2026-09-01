package junit.com.svenruppert.urlshortener.api.security.oidc;

import com.svenruppert.functional.result.Result;
import eu.jsentinel.jcustos.authorization.api.JCustosSubject;
import eu.jsentinel.jcustos.oidc.api.IdTokenValidationError;
import eu.jsentinel.jcustos.test.oidc.StubIdentityProvider;
import com.svenruppert.urlshortener.api.security.oidc.OidcSubjectBridge;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P270: proves the OIDC → subject/role bridge against a real (stub) identity
 * provider — no mocks. The stub serves a JWKS endpoint and signs ID tokens;
 * the bridge fetches the JWKS over HTTP, validates the token and maps claims.
 */
class OidcSubjectBridgeTest {

  @Test
  void mapsStubIdTokenToSubjectWithRoles()
      throws Exception {
    try (StubIdentityProvider idp = StubIdentityProvider.start("url-shortener-test", Instant::now)) {
      long now = Instant.now().getEpochSecond();
      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("sub", "oidc-user-1");
      claims.put("iss", idp.issuer());
      claims.put("aud", idp.clientId());
      claims.put("email", "sso@example.com");
      claims.put("roles", List.of("USER"));
      claims.put("iat", now);
      claims.put("exp", now + 3600);
      String idToken = idp.signIdToken(claims);

      Result<JCustosSubject, IdTokenValidationError> result =
          OidcSubjectBridge.authenticate(idToken, idp.jwksUri(), idp.issuer(), idp.clientId());

      assertTrue(result.isSuccess(),
          () -> "expected a valid subject, got " + result);
      JCustosSubject subject = result.getOrThrow();
      // The mapper namespaces the subject per issuer (issuer#sub) — OIDC subs
      // are only unique within their issuer.
      assertTrue(subject.subjectId().endsWith("#oidc-user-1"),
          () -> "subject id derives from the sub claim, was " + subject.subjectId());
      assertTrue(subject.roleNames().stream().anyMatch(r -> r.value().equals("USER")),
          "USER role mapped from the roles claim");
    }
  }

  @Test
  void rejectsTokenFromWrongIssuer()
      throws Exception {
    try (StubIdentityProvider idp = StubIdentityProvider.start("url-shortener-test", Instant::now)) {
      long now = Instant.now().getEpochSecond();
      String idToken = idp.signIdToken(Map.of(
          "sub", "oidc-user-2", "iss", idp.issuer(), "aud", idp.clientId(),
          "iat", now, "exp", now + 3600));

      Result<JCustosSubject, IdTokenValidationError> result = OidcSubjectBridge.authenticate(
          idToken, idp.jwksUri(), "https://attacker.example", idp.clientId());

      assertFalse(result.isSuccess(), "token with an unexpected issuer is rejected");
    }
  }
}
