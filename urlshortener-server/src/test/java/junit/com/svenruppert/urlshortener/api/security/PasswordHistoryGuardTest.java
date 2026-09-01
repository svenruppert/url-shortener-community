package junit.com.svenruppert.urlshortener.api.security;

import eu.jsentinel.jcustos.credential.history.PasswordHistoryPolicy;
import com.svenruppert.urlshortener.api.security.PasswordHistoryGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHistoryGuardTest {

  @Test
  void recordedPassword_isDetectedAsReuse() {
    PasswordHistoryGuard guard = new PasswordHistoryGuard(PasswordHistoryPolicy.retain(5));
    assertFalse(guard.isReuse("u1", "first-pass-1"), "nothing recorded yet");

    guard.record("u1", "first-pass-1");
    assertTrue(guard.isReuse("u1", "first-pass-1"), "exact previous password is a reuse");
    assertFalse(guard.isReuse("u1", "brand-new-pass"), "a fresh password is fine");
  }

  @Test
  void historyIsPerUser() {
    PasswordHistoryGuard guard = new PasswordHistoryGuard(PasswordHistoryPolicy.retain(5));
    guard.record("alice", "alice-secret");
    assertTrue(guard.isReuse("alice", "alice-secret"));
    assertFalse(guard.isReuse("bob", "alice-secret"), "bob has his own history");
  }
}
