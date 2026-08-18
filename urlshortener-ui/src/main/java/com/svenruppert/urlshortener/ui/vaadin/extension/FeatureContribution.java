package com.svenruppert.urlshortener.ui.vaadin.extension;

import java.util.List;

/**
 * ServiceLoader SPI for UI feature plug-ins (open-core seam, concept
 * DECISION 2). An Enterprise jar on the classpath registers an implementation
 * under {@code META-INF/services/com.svenruppert.urlshortener.ui.vaadin.extension.FeatureContribution}
 * and thereby adds views, drawer entries and navbar components — without any
 * change to community sources. With no plug-ins present, every accessor
 * returns an empty list and the application behaves exactly as before.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface FeatureContribution {

  /** Stable identifier of the contributed feature (diagnostics/logging). */
  String featureId();

  default List<RouteContribution> routes() {
    return List.of();
  }

  default List<MenuContribution> menuItems() {
    return List.of();
  }

  default List<NavbarContribution> navbarComponents() {
    return List.of();
  }
}
