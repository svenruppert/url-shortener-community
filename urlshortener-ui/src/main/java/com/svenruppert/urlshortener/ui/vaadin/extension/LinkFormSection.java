package com.svenruppert.urlshortener.ui.vaadin.extension;

import com.vaadin.flow.component.Component;

import java.util.List;

/**
 * One plug-in section inside a link-create form: the component is added to
 * the form layout, and after a successful create the section is told which
 * short codes were created so it can apply its own metadata via its own
 * endpoints.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface LinkFormSection {

  Component component();

  /** Called after a successful create with the created short codes (single create: one element). */
  void onLinksCreated(List<String> shortCodes);
}
