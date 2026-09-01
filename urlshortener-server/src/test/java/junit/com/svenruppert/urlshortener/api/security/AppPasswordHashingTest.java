package junit.com.svenruppert.urlshortener.api.security;

import eu.jsentinel.jcustos.authentication.PasswordHasher;
import com.svenruppert.urlshortener.api.security.AppPasswordHashing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPasswordHashingTest {

  @Test
  void hashVerifyRoundtrip() {
    PasswordHasher h = AppPasswordHashing.hasher();
    String hash = h.hash("secret123".toCharArray());
    assertTrue(hash != null && !hash.isBlank(), "produces an encoded hash");
    assertTrue(h.verify("secret123".toCharArray(), hash), "correct password verifies");
    assertFalse(h.verify("wrong".toCharArray(), hash), "wrong password rejected");
  }

  @Test
  void dummyVerify_doesNotThrow() {
    assertDoesNotThrow(() -> AppPasswordHashing.dummyVerify("anything".toCharArray()));
    assertDoesNotThrow(() -> AppPasswordHashing.dummyVerify(null));
  }
}
