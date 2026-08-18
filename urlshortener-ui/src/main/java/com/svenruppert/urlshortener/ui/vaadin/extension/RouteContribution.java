package com.svenruppert.urlshortener.ui.vaadin.extension;

import com.vaadin.flow.component.Component;

/**
 * A dynamically registered route contributed by an (Enterprise) plug-in jar.
 * Community views keep their static {@code @Route} annotations (concept
 * DECISION 3, hybrid); only contributed views are registered at runtime by
 * {@link OpenCoreRouteInitializer}, always under the application's
 * {@code MainLayout}.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @param path the route path (without leading slash), e.g. {@code audit-export}
 * @param view the view component class to register
 * @since 00.18.00
 */
public record RouteContribution(String path, Class<? extends Component> view) {
}
