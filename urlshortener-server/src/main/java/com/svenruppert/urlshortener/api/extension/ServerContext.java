package com.svenruppert.urlshortener.api.extension;

import com.svenruppert.urlshortener.api.store.campaign.CampaignStore;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;

/**
 * The server-side services handed to {@link HandlerContribution}s so plug-in
 * endpoints work against the SAME stores as the community handlers —
 * regardless of the in-memory/persistent mode the server was started with.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public record ServerContext(UrlMappingStore urlMappingStore,
                            CampaignStore campaignStore,
                            StatisticsStore statisticsStore,
                            PreferencesStore preferencesStore) {
}
