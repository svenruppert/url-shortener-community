package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.security.RateLimitGuard;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitGuardTest {

  @Test
  void allowsUpToLimit_thenThrottles() {
    RateLimitGuard guard = new RateLimitGuard(3, Duration.ofMinutes(1));
    assertTrue(guard.allow("s"), "1st within limit");
    assertTrue(guard.allow("s"), "2nd within limit");
    assertTrue(guard.allow("s"), "3rd within limit");
    assertFalse(guard.allow("s"), "4th over limit");
  }

  @Test
  void scopesAreIndependent() {
    RateLimitGuard guard = new RateLimitGuard(1, Duration.ofMinutes(1));
    assertTrue(guard.allow("a"));
    assertTrue(guard.allow("b"));   // different scope, own budget
    assertFalse(guard.allow("a"));  // scope a exhausted
  }

  @Test
  void developmentProfile_disablesTheSingleton() {
    // Tests run under the default (development) profile → guard disabled.
    assertFalse(RateLimitGuard.INSTANCE.enabled());
    assertTrue(RateLimitGuard.INSTANCE.allow("anything"));
  }
}
