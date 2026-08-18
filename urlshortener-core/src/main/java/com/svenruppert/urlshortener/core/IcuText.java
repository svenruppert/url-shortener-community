package com.svenruppert.urlshortener.core;

import com.ibm.icu.text.MessageFormat;

import java.util.Locale;
import java.util.Map;

/**
 * Locale-aware message formatting with ICU4J — supports {@code plural}/
 * {@code select} patterns that {@link java.text.MessageFormat} cannot express
 * (P050). Pure/stateless, so it is unit-testable without a UI.
 *
 * <p>Example pattern:
 * {@code {count, plural, one {# entry} other {# entries}}}
 */
public final class IcuText {

  private IcuText() {
  }

  /** Formats an ICU pattern with named arguments for the given locale. */
  public static String format(String pattern, Locale locale, Map<String, Object> args) {
    if (pattern == null) return "";
    Locale effective = locale == null ? Locale.getDefault() : locale;
    return new MessageFormat(pattern, effective).format(args == null ? Map.of() : args);
  }

  /** Convenience for the common single-count plural case. */
  public static String count(String pattern, Locale locale, String argName, Number count) {
    return format(pattern, locale, Map.of(argName, count));
  }
}
