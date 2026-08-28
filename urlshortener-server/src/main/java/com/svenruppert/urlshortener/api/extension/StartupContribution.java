package com.svenruppert.urlshortener.api.extension;

/**
 * ServiceLoader SPI invoked ONCE per JVM as the very first step of
 * {@code ShortenerServer.init(...)} — BEFORE the security bootstrap opens the
 * jSentinel storages and before the app store opens. The hook exists for
 * plug-ins that must act on the raw storage directories while nothing holds
 * them open (V00.18.00 P090: restore-at-startup). Implementations must be
 * fast and must never throw — a failure is logged and startup continues.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface StartupContribution {

  void beforeStoresOpen();
}
