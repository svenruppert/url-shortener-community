package com.svenruppert.urlshortener.api.extension;

/**
 * ServiceLoader SPI: a plug-in jar registers an implementation under
 * {@code META-INF/services/com.svenruppert.urlshortener.api.extension.LinkLifecycleContribution}
 * to receive link lifecycle callbacks. Discovered once at server start with
 * the same {@link ServerContext} the handler contributions get.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface LinkLifecycleContribution {

  LinkLifecycleListener create(ServerContext context);
}
