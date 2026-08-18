package com.svenruppert.urlshortener.api.store.preferences;

import java.util.Map;

public interface PreferencesUpdater {
  void saveColumnVisibilities(String userId, String viewId, Map<String, Boolean> visibility);
  void delete(String userId, String viewId, String columnKey);
  void delete(String userId, String viewId);
  void delete(String userId);
}
