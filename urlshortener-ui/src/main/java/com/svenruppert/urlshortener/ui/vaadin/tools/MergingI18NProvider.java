package com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.ui.vaadin.extension.I18nBundleContribution;
import com.vaadin.flow.i18n.I18NProvider;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.ServiceLoader;

/**
 * An {@link I18NProvider} that merges the community base translation bundle with
 * one bundle per {@link I18nBundleContribution} discovered on the classpath, so
 * every (Enterprise) plug-in ships its own translations and the community
 * edition never has to carry enterprise strings.
 *
 * <p>Resolution order: the community base ({@value #COMMUNITY_BASE}) first, then
 * the contributed bundles in discovery order. The first bundle that defines the
 * key wins; key namespaces are expected to be disjoint, so order only matters as
 * a safety rule. A miss returns the key itself — the contract the
 * {@link I18n#tr(String, String, Object...)} facade and the
 * {@code I18nSupport} mixin rely on to substitute their inline fallback.
 *
 * <p>Registered via the {@code i18n.provider} servlet init-param (see
 * {@code web.xml}); {@code META-INF/services} for {@link I18NProvider} is
 * ignored by Vaadin 25. Uses the no-fallback {@link ResourceBundle.Control} so a
 * request for one locale never silently serves another (the JVM-default trap).
 *
 * @since 00.23.00
 */
public final class MergingI18NProvider
    implements I18NProvider, HasLogger {

  /** Community base bundle: {@code vaadin-i18n/translations_<locale>.properties}. */
  static final String COMMUNITY_BASE = "vaadin-i18n.translations";

  private static final List<Locale> SUPPORTED =
      List.of(Locale.ENGLISH, Locale.GERMAN, Locale.of("fi"));

  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  private final List<String> bundleBases;

  public MergingI18NProvider() {
    List<String> bases = new ArrayList<>();
    bases.add(COMMUNITY_BASE);
    for (I18nBundleContribution contribution : ServiceLoader.load(
        I18nBundleContribution.class, Thread.currentThread().getContextClassLoader())) {
      String base = contribution.bundleBaseName();
      if (base != null && !base.isBlank() && !bases.contains(base)) {
        bases.add(base);
      }
    }
    this.bundleBases = List.copyOf(bases);
    logger().info("MergingI18NProvider ready — merging {} bundle(s): {}",
        bundleBases.size(), bundleBases);
  }

  @Override
  public List<Locale> getProvidedLocales() {
    return SUPPORTED;
  }

  @Override
  public String getTranslation(String key, Locale locale, Object... params) {
    if (key == null) return "";
    Locale lookup = supported(locale);
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    for (String base : bundleBases) {
      try {
        ResourceBundle bundle = ResourceBundle.getBundle(base, lookup, loader, CONTROL);
        if (bundle.containsKey(key)) {
          String value = bundle.getString(key);
          return (params == null || params.length == 0)
              ? value : MessageFormat.format(value, params);
        }
      } catch (MissingResourceException noBundleForLocale) {
        // This bundle has no file for the requested locale — try the next one.
      }
    }
    // Miss: return the key so the tr(...) facade substitutes its inline fallback.
    return key;
  }

  private static Locale supported(Locale requested) {
    if (requested != null) {
      for (Locale s : SUPPORTED) {
        if (s.getLanguage().equals(requested.getLanguage())) return requested;
      }
    }
    return Locale.ENGLISH;
  }
}
