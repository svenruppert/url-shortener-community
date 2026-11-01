package com.svenruppert.urlshortener.ui.vaadin.extension;

/**
 * Open-core SPI: an (Enterprise) plug-in jar contributes its own i18n resource
 * bundle so its translation keys never have to live in the community edition's
 * translation files.
 *
 * <p>Each contribution names a {@link java.util.ResourceBundle} base, e.g.
 * {@code "i18n.enterprise-backup"}, which resolves to
 * {@code i18n/enterprise-backup_<locale>.properties} on the plug-in's
 * classpath. {@link com.svenruppert.urlshortener.ui.vaadin.tools.MergingI18NProvider}
 * discovers all implementations via {@link java.util.ServiceLoader} and merges
 * them on top of the community base bundle — keeping community sources free of
 * enterprise strings.
 *
 * <p>Base names must be unique across plug-ins (so the resources do not
 * collide on the shared classpath) and key namespaces should not overlap the
 * community keys; the provider resolves a key from the first bundle that
 * defines it, community base first.
 *
 * <p>Register an implementation via
 * {@code META-INF/services/com.svenruppert.urlshortener.ui.vaadin.extension.I18nBundleContribution}.
 *
 * @since 00.23.00
 */
public interface I18nBundleContribution {

  /**
   * The {@link java.util.ResourceBundle} base name for this plug-in's
   * translations, e.g. {@code "i18n.enterprise-backup"} for
   * {@code i18n/enterprise-backup_de.properties}. Must be unique per plug-in.
   */
  String bundleBaseName();
}
