package junit.com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.urlshortener.ui.vaadin.tools.MergingI18NProvider;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-mocks test of the merging provider: a real community base bundle plus a
 * real, ServiceLoader-discovered {@link TestI18nBundle} on the test classpath.
 */
class MergingI18NProviderTest {

  private final MergingI18NProvider provider = new MergingI18NProvider();

  @Test
  void resolvesKeysFromAContributedPluginBundle() {
    assertEquals("Merged English",
        provider.getTranslation("merge.test.key", Locale.ENGLISH));
    assertEquals("Merged Deutsch",
        provider.getTranslation("merge.test.key", Locale.GERMAN));
  }

  @Test
  void resolvesKeysFromTheCommunityBaseBundle() {
    // common.* stays in the community edition and must still resolve.
    assertEquals("Schließen", provider.getTranslation("common.close", Locale.GERMAN));
    assertEquals("Close", provider.getTranslation("common.close", Locale.ENGLISH));
  }

  @Test
  void returnsTheKeyOnAMiss() {
    // The tr(...) facade relies on this to substitute its inline fallback.
    assertEquals("no.such.key.anywhere",
        provider.getTranslation("no.such.key.anywhere", Locale.ENGLISH));
  }

  @Test
  void unsupportedLocaleFallsBackToEnglishNotToTheJvmDefault() {
    // Spanish is unsupported → English, never a JVM-default (e.g. German) leak.
    assertEquals("Close", provider.getTranslation("common.close", Locale.of("es")));
  }

  @Test
  void advertisesTheSupportedLocales() {
    assertTrue(provider.getProvidedLocales().contains(Locale.ENGLISH));
    assertTrue(provider.getProvidedLocales().contains(Locale.GERMAN));
    assertTrue(provider.getProvidedLocales().contains(Locale.of("fi")));
  }
}
