package junit.com.svenruppert.urlshortener.core;

import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.core.AliasPolicy.Reason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasPolicyTest {

  @Test
  void validAlias_passes() {
    assertTrue(AliasPolicy.validate("news-2025").valid());
    assertTrue(AliasPolicy.validate("promo_x").valid());
    assertTrue(AliasPolicy.validate("abc").valid());
  }

  @Test
  void blankOrNull_fails() {
    assertEquals(Reason.NULL_OR_BLANK, AliasPolicy.validate(null).reason());
    assertEquals(Reason.NULL_OR_BLANK, AliasPolicy.validate("  ").reason());
  }

  @Test
  void tooShortOrTooLong_fails() {
    assertEquals(Reason.TOO_SHORT, AliasPolicy.validate("ab").reason());
    assertEquals(Reason.TOO_LONG, AliasPolicy.validate("a".repeat(AliasPolicy.MAX + 1)).reason());
  }

  @Test
  void invalidChars_fails() {
    assertEquals(Reason.INVALID_CHARS, AliasPolicy.validate("bad alias").reason());
    assertEquals(Reason.INVALID_CHARS, AliasPolicy.validate("has/slash").reason());
  }

  @Test
  void baselineReservedAliases_fail_caseInsensitive() {
    assertEquals(Reason.RESERVED, AliasPolicy.validate("api").reason());
    assertEquals(Reason.RESERVED, AliasPolicy.validate("Login").reason());
    assertEquals(Reason.RESERVED, AliasPolicy.validate("USERS").reason());
  }

  @Test
  void reserve_isAdditiveAndDynamic() {
    String alias = "dynamicroute";
    assertTrue(AliasPolicy.validate(alias).valid(), "not reserved yet");

    AliasPolicy.reserve(List.of("DynamicRoute"));
    assertFalse(AliasPolicy.validate(alias).valid(), "now reserved (case-insensitive)");
    assertEquals(Reason.RESERVED, AliasPolicy.validate(alias).reason());
    assertTrue(AliasPolicy.reserved().contains("dynamicroute"));

    // baseline still present after merge
    assertTrue(AliasPolicy.reserved().contains("api"));
  }

  @Test
  void reason_messageKey_isStableAndNamespaced() {
    assertEquals("alias.reason.reserved", Reason.RESERVED.messageKey());
    assertEquals("alias.reason.too_short", Reason.TOO_SHORT.messageKey());
  }

  @Test
  void normalize_lowercases() {
    assertEquals("mixedcase", AliasPolicy.normalize("MixedCase"));
  }
}
