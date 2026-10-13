package junit.com.svenruppert.urlshortener.core;

import com.svenruppert.urlshortener.core.JsonUtils;
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

  @Test
  void jsonCarriesProtectedFlagButNeverTheHash() {
    var json = JsonUtils.toJson(protectedMapping("SUPER-SECRET-HASH"));
    assertFalse(json.contains("SUPER-SECRET-HASH"), "hash must never serialize: " + json);
    assertFalse(json.contains("passwordHash"), "hash property must be absent: " + json);
    assertTrue(json.contains("\"passwordProtected\":true"), "protected flag must serialize: " + json);
  }

  @Test
  void protectedFlagSurvivesRoundTripWhileTheHashDoesNot() {
    // Client shape: the server sends passwordProtected but not the hash, so the
    // deserialized object knows it is protected yet holds no hash.
    var json = JsonUtils.toJson(protectedMapping("HASH"));
    var client = JsonUtils.fromJson(json, ShortUrlMapping.class);
    assertTrue(client.isPasswordProtected(), "client must see the protected flag");
    assertNull(client.getPasswordHash(), "client must not receive the hash");
  }

  @Test
  void unprotectedMappingSerializesProtectedFalse() {
    var m = new ShortUrlMapping("abc", "https://example.org", Instant.EPOCH, null, true);
    var json = JsonUtils.toJson(m);
    assertTrue(json.contains("\"passwordProtected\":false"), "unprotected flag must be false: " + json);
    assertFalse(m.isPasswordProtected());
  }
}
