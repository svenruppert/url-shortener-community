package com.svenruppert.urlshortener.api.store.preferences;

import java.util.Map;

public interface PreferencesLookup {

  Map<String, Boolean> load(String userId, String viewId);

}
