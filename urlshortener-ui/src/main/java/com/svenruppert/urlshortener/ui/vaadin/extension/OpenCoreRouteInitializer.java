package com.svenruppert.urlshortener.ui.vaadin.extension;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * Registers the {@link RouteContribution}s of every discovered
 * {@link FeatureContribution} at application scope, always under
 * {@link MainLayout}. Community views keep their static {@code @Route}
 * annotations (concept DECISION 3, hybrid) — this initializer only ever adds
 * plug-in routes and is a no-op without plug-ins on the classpath.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public class OpenCoreRouteInitializer
    implements VaadinServiceInitListener, HasLogger {

  @Override
  public void serviceInit(ServiceInitEvent event) {
    var routes = FeatureRegistry.routes();
    if (routes.isEmpty()) {
      return;
    }
    RouteConfiguration configuration = RouteConfiguration.forApplicationScope();
    for (RouteContribution route : routes) {
      configuration.setRoute(route.path(), route.view(), MainLayout.class);
      logger().info("contributed route registered: /{} -> {}", route.path(), route.view().getName());
    }
  }
}
