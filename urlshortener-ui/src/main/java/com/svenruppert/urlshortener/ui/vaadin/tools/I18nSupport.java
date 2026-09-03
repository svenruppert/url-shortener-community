package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.svenruppert.urlshortener.core.IcuText;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;

public interface I18nSupport {

  default String tr(String key) {
    return ((Component) this).getTranslation(key);
  }

  default String tr(String key, String fallback) {
    final String translated = ((Component) this).getTranslation(key);
    if (translated == null || translated.isBlank() || translated.equals(key)) {
      return fallback;
    }
    return translated;
  }

  default String tr(String key, String fallback, Object... params) {
    final String translated = ((Component) this).getTranslation(key, params);
    if (translated == null || translated.isBlank() || translated.equals(key)) {
      // fallback uses the same placeholder style as Vaadin (MessageFormat)
      return MessageFormat.format(fallback, params);
    }
    return translated;
  }

  /**
   * Locale-aware plural/select formatting via ICU4J (P050). The value stored
   * under {@code key} is an ICU pattern, e.g.
   * {@code {count, plural, one {# entry} other {# entries}}}. The raw pattern is
   * fetched without Vaadin's MessageFormat pass (no params) and formatted with
   * {@link IcuText} for the component's current locale. Falls back to
   * {@code fallbackPattern} when the key is missing.
   */
  default String trPlural(String key, String fallbackPattern, Map<String, Object> args) {
    final Component self = (Component) this;
    final String pattern = self.getTranslation(key);
    final Locale locale = self.getUI()
        .map(UI::getLocale)
        .orElse(Locale.getDefault());
    final boolean missing = pattern == null || pattern.isBlank() || pattern.equals(key);
    return IcuText.format(missing ? fallbackPattern : pattern, locale, args);
  }
}