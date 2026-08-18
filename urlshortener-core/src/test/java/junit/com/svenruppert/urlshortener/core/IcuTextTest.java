package junit.com.svenruppert.urlshortener.core;

import com.svenruppert.urlshortener.core.IcuText;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IcuTextTest {

  private static final String PATTERN_EN = "{count, plural, one {# entry} other {# entries}}";
  private static final String PATTERN_DE = "{count, plural, one {# Eintrag} other {# Einträge}}";

  @Test
  void english_pluralBranches() {
    assertEquals("1 entry", IcuText.count(PATTERN_EN, Locale.ENGLISH, "count", 1));
    assertEquals("0 entries", IcuText.count(PATTERN_EN, Locale.ENGLISH, "count", 0));
    assertEquals("5 entries", IcuText.count(PATTERN_EN, Locale.ENGLISH, "count", 5));
  }

  @Test
  void german_pluralBranches() {
    assertEquals("1 Eintrag", IcuText.count(PATTERN_DE, Locale.GERMAN, "count", 1));
    assertEquals("3 Einträge", IcuText.count(PATTERN_DE, Locale.GERMAN, "count", 3));
  }

  @Test
  void namedArgs_areSubstituted() {
    assertEquals("Hi Sven", IcuText.format("Hi {name}", Locale.ENGLISH, Map.of("name", "Sven")));
  }

  @Test
  void nullPattern_isEmpty() {
    assertEquals("", IcuText.format(null, Locale.ENGLISH, Map.of()));
  }
}
