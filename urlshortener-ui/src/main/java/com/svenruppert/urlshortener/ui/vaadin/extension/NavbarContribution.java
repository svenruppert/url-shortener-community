package com.svenruppert.urlshortener.ui.vaadin.extension;

import com.vaadin.flow.component.Component;

/**
 * A navbar component contributed by an (Enterprise) plug-in jar, appended to
 * the application navbar after the community components. The factory is
 * invoked once per {@code MainLayout} instance (i.e. per UI).
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
@FunctionalInterface
public interface NavbarContribution {

  Component create();
}
