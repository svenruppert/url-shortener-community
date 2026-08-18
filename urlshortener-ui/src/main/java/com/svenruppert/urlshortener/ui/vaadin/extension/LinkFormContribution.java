package com.svenruppert.urlshortener.ui.vaadin.extension;

/**
 * Factory for plug-in sections in the link-create forms (Create view and
 * Bulk-Create view). Each form instance requests a fresh
 * {@link LinkFormSection} so the section can hold per-form state.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface LinkFormContribution {

  LinkFormSection createSection();
}
