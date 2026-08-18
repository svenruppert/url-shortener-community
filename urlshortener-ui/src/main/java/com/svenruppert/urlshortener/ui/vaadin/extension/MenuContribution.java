package com.svenruppert.urlshortener.ui.vaadin.extension;

import com.vaadin.flow.component.icon.VaadinIcon;

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
 * @since 00.18.00
 */
public record MenuContribution(String i18nKey, String fallback, String path,
                               VaadinIcon icon, boolean adminOnly) {
}
