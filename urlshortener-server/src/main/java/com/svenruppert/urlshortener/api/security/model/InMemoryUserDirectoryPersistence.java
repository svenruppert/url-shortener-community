package com.svenruppert.urlshortener.api.security.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link UserDirectoryPersistence} for tests and transient
 * setups. State lives in a single map, dropped on {@link #close()};
 * nothing reaches disk.
 */
public final class InMemoryUserDirectoryPersistence implements UserDirectoryPersistence {

  private Map<String, StoredUser> state = new LinkedHashMap<>();

  @Override
  public Map<String, StoredUser> load() {
    return new LinkedHashMap<>(state);
  }

  @Override
  public void save(Map<String, StoredUser> snapshot) {
    state = new LinkedHashMap<>(snapshot);
  }

  @Override
  public void close() {
    state.clear();
  }
}
