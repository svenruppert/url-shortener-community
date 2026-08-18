package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.security.TokenStore;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenStoreTimeoutTest {

  private static final ApiUser USER = new ApiUser(1L, "u", Set.of());
  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  /** Advanceable clock for deterministic expiry testing. */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration d) {
      this.now = this.now.plus(d);
    }

    @Override public Instant instant() {
      return now;
    }

    @Override public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  @Test
  void isExpired_pure() {
    assertFalse(TokenStore.isExpired(T0, null, T0.plus(Duration.ofDays(365))), "null maxAge never expires");
    assertFalse(TokenStore.isExpired(T0, Duration.ofHours(1), T0.plus(Duration.ofMinutes(59))));
    assertTrue(TokenStore.isExpired(T0, Duration.ofHours(1), T0.plus(Duration.ofMinutes(61))));
  }

  @Test
  void resolve_dropsExpiredToken() {
    MutableClock clock = new MutableClock(T0);
    TokenStore store = new TokenStore(Duration.ofMinutes(30), clock);
    String token = store.issue(USER, 0L);

    assertTrue(store.resolve(token).isPresent(), "valid within window");
    assertTrue(store.versionSnapshot(token).isPresent());

    clock.advance(Duration.ofMinutes(31));
    assertTrue(store.resolve(token).isEmpty(), "expired after window");
    assertTrue(store.versionSnapshot(token).isEmpty(), "snapshot also gone after expiry");
  }

  @Test
  void noExpiry_whenMaxAgeNull() {
    MutableClock clock = new MutableClock(T0);
    TokenStore store = new TokenStore(null, clock);
    String token = store.issue(USER, 0L);
    clock.advance(Duration.ofDays(3650));
    assertTrue(store.resolve(token).isPresent(), "no expiry configured -> never times out");
  }
}
