package junit.com.svenruppert.urlshortener.core;

import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The optional per-link password is modelled exactly like expiry: a single
 * nullable field that every copy-with carries along. These tests pin that the
 * hash round-trips, survives unrelated edits, and never leaks via toString.
 */
class ShortUrlMappingPasswordTest {

  private static ShortUrlMapping protectedMapping(String hash) {
    return new ShortUrlMapping("abc", "https://example.org", Instant.EPOCH, null, true, hash);
  }

  @Test
  void passwordHashRoundTripsThroughTheSixArgConstructor() {
    var m = protectedMapping("HASH");
    assertEquals("HASH", m.getPasswordHash());
    assertTrue(m.passwordHash().isPresent());
    assertEquals("HASH", m.passwordHash().get());
  }

  @Test
  void fiveArgConstructorLeavesLinkUnprotected() {
    var m = new ShortUrlMapping("abc", "https://example.org", Instant.EPOCH, null, true);
    assertNull(m.getPasswordHash());
    assertTrue(m.passwordHash().isEmpty());
  }

  @Test
  void withExpiresAtPreservesPassword() {
    var later = protectedMapping("HASH").withExpiresAt(Instant.now().plusSeconds(3600));
    assertEquals("HASH", later.getPasswordHash(), "withExpiresAt must preserve the password hash");
  }

  @Test
  void withActivePreservesPassword() {
    var toggled = protectedMapping("HASH").withActive(false);
    assertEquals("HASH", toggled.getPasswordHash(), "withActive must preserve the password hash");
  }

  @Test
  void withPasswordHashReplacesOnlyThePassword() {
    var changed = protectedMapping("OLD").withPasswordHash("NEW");
    assertEquals("NEW", changed.getPasswordHash());
    assertEquals("abc", changed.shortCode());
    assertNull(protectedMapping("OLD").withPasswordHash(null).getPasswordHash());
  }

  @Test
  void toStringNeverContainsTheHash() {
    var s = protectedMapping("SUPER-SECRET-HASH").toString();
    assertFalse(s.contains("SUPER-SECRET-HASH"), "toString must not leak the hash: " + s);
    assertTrue(s.contains("passwordProtected=true"), "toString should mark protection: " + s);
  }

  @Test
  void equalsAndHashCodeAccountForPassword() {
    var base = protectedMapping("H");
    var same = protectedMapping("H");
    var other = protectedMapping("OTHER");
    assertEquals(base, same);
    assertEquals(base.hashCode(), same.hashCode());
    assertNotEquals(base, other);
  }
}
