package com.svenruppert.urlshortener.ui.vaadin.extension;

import com.vaadin.flow.component.Component;

/**
 * One plug-in section inside the link-details dialog. The component renders
 * below the community fields; the default hooks let the section follow the
 * dialog's edit/save/cancel cycle without the dialog knowing the feature.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface LinkDetailsSection {

  Component component();

  /** The dialog switched between read-only and edit mode. */
  default void editMode(boolean enabled) {
  }

  /** Applies pending edits; returns {@code true} when a change was saved. */
  default boolean onSave(String shortCode) {
    return false;
  }

  /** The dialog discarded unsaved edits. */
  default void onDiscard() {
  }
}
