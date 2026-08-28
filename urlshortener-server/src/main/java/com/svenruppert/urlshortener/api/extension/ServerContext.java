package com.svenruppert.urlshortener.api.extension;

import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;

/**
 * The server-side services handed to {@link HandlerContribution}s so plug-in
 * endpoints work against the SAME stores as the community handlers —
 * regardless of the in-memory/persistent mode the server was started with.
 * The {@link LinkMetaStorage} seam replaces the pre-split campaign store
 * (V00.18.00 P085): the feature logic lives in the plug-in, the host only
 * hands out the raw persistent maps.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public record ServerContext(UrlMappingStore urlMappingStore,
                            LinkMetaStorage linkMetaStorage,
                            StatisticsStore statisticsStore,
                            PreferencesStore preferencesStore,
                            StorageAdmin storageAdmin) {
}
