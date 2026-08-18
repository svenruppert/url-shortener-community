package com.svenruppert.urlshortener.api.store.imports;

import java.util.Optional;

public interface ImportStagingStore {
  String put(ImportStaging staging);
  Optional<ImportStaging> get(String id);
  void remove(String id);
}
