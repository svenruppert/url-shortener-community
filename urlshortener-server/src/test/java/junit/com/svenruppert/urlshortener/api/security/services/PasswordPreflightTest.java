package junit.com.svenruppert.urlshortener.api.security.services;

import com.svenruppert.urlshortener.api.security.services.PasswordPreflight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPreflightTest {

  @Test
  void rejectsLocalBlocklist() {
    assertFalse(PasswordPreflight.isAcceptable("password"));
    assertFalse(PasswordPreflight.isAcceptable("admin123"));
  }

  @Test
  void rejectsNullOrEmpty() {
    assertFalse(PasswordPreflight.isAcceptable(null));
    assertFalse(PasswordPreflight.isAcceptable(""));
  }

  @Test
  void acceptsStrongPassword_offlineInDevProfile() {
    // Default (development) profile -> no HIBP network call.
    assertTrue(PasswordPreflight.isAcceptable("a-Very-Unique-Passphrase-9271!"));
  }
}
