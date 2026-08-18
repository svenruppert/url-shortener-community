package com.svenruppert.urlshortener.api.store.preferences;

import com.svenruppert.dependencies.core.logger.HasLogger;

public interface PreferencesStore
    extends PreferencesLookup, PreferencesUpdater, HasLogger {
}
