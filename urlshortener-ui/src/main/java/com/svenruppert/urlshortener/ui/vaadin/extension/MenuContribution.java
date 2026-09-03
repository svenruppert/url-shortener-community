package com.svenruppert.urlshortener.ui.vaadin.extension;

import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.vaadin.flow.component.icon.VaadinIcon;

import java.util.Set;

/**
 * A side-navigation entry contributed by an (Enterprise) plug-in jar,
 * appended to the drawer after the community entries.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @param i18nKey   translation key for the label
 * @param fallback  fallback label when no translation exists
 * @param path      navigation target (without leading slash)
 * @param icon      icon shown next to the label; may be {@code null}
 * @param adminOnly when {@code true} the entry is rendered for ADMIN users
 *                  only (server-side access control stays authoritative)
 * @param alsoVisibleFor additional roles that see the entry despite
 *                  {@code adminOnly} — for features aimed at a narrow role
 *                  rather than at everyone (V00.19.00 P050). Never widens
 *                  access; the server still decides what the role may read.
 * @since 00.18.00
 */
public record MenuContribution(String i18nKey, String fallback, String path,
                               VaadinIcon icon, boolean adminOnly,
                               Set<AppRole> alsoVisibleFor) {

  public MenuContribution {
    alsoVisibleFor = alsoVisibleFor == null ? Set.of() : Set.copyOf(alsoVisibleFor);
  }

  /** Admin-or-everyone entry, the shape used before V00.19.00. */
  public MenuContribution(String i18nKey, String fallback, String path,
                          VaadinIcon icon, boolean adminOnly) {
    this(i18nKey, fallback, path, icon, adminOnly, Set.of());
  }
}
