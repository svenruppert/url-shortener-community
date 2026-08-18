package com.svenruppert.urlshortener.api.store.imports;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryImportStagingStore
    implements ImportStagingStore {

  private final ConcurrentHashMap<String, ImportStaging> map = new ConcurrentHashMap<>();

  @Override
  public String put(ImportStaging staging) {
    String id = UUID.randomUUID().toString();
    map.put(id, staging);
    return id;
  }

  @Override
  public Optional<ImportStaging> get(String id) {
    return Optional.ofNullable(map.get(id));
  }

  @Override
  public void remove(String id) {
    map.remove(id);
  }
}
