package com.svenruppert.urlshortener.ui.vaadin.extension;

/**
 * Factory for plug-in sections in the link-details dialog. Each dialog
 * instance requests a fresh {@link LinkDetailsSection} for the link it shows,
 * so the section can hold per-dialog state and participate in the dialog's
 * edit/save/cancel cycle.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface LinkDetailsContribution {

  LinkDetailsSection createSection(String shortCode);
}
