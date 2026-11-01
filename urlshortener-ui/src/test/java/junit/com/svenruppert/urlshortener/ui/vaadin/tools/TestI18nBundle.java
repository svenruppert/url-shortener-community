package junit.com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.urlshortener.ui.vaadin.extension.I18nBundleContribution;

/**
 * Test-only i18n bundle contribution, discovered via the test-classpath
 * {@code META-INF/services} entry, used by {@code MergingI18NProviderTest} to
 * prove a plug-in bundle is merged on top of the community base.
 */
public final class TestI18nBundle
    implements I18nBundleContribution {

  @Override
  public String bundleBaseName() {
    return "i18n.test-merge";
  }
}
