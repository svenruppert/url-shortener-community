package com.svenruppert.urlshortener.ui.vaadin.extension;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers {@link FeatureContribution} plug-ins via {@link ServiceLoader}
 * once per JVM and exposes their aggregated contributions. Community code
 * only ever reads from this registry — an empty classpath yields empty lists
 * and zero behavior change.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public final class FeatureRegistry {

  private FeatureRegistry() {
  }

  private static final class Holder {
    private static final List<FeatureContribution> ALL = load();

    private static List<FeatureContribution> load() {
      List<FeatureContribution> found = new ArrayList<>();
      ServiceLoader.load(FeatureContribution.class).forEach(found::add);
      found.forEach(c -> HasLogger.staticLogger()
          .info("ui feature contribution discovered: '{}' ({} routes, {} menu items, {} navbar components)",
              c.featureId(), c.routes().size(), c.menuItems().size(), c.navbarComponents().size()));
      return List.copyOf(found);
    }
  }

  public static List<FeatureContribution> all() {
    return Holder.ALL;
  }

  public static List<RouteContribution> routes() {
    return Holder.ALL.stream().flatMap(c -> c.routes().stream()).toList();
  }

  public static List<MenuContribution> menuItems() {
    return Holder.ALL.stream().flatMap(c -> c.menuItems().stream()).toList();
  }

  public static List<NavbarContribution> navbarComponents() {
    return Holder.ALL.stream().flatMap(c -> c.navbarComponents().stream()).toList();
  }

  public static List<LinkFormContribution> linkFormContributions() {
    return Holder.ALL.stream().flatMap(c -> c.linkFormContributions().stream()).toList();
  }

  public static List<LinkDetailsContribution> linkDetailsContributions() {
    return Holder.ALL.stream().flatMap(c -> c.linkDetailsContributions().stream()).toList();
  }
}
