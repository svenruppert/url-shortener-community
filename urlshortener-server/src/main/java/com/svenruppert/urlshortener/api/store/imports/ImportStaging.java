package com.svenruppert.urlshortener.api.store.imports;

import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.time.Instant;
import java.util.List;

public record ImportStaging(
    Instant createdAt,
    List<ShortUrlMapping> newItems,
    List<Conflict> conflicts,
    List<InvalidItem> invalidItems
) {
  public record Conflict(String shortCode, ShortUrlMapping existing, ShortUrlMapping incoming) { }

  public record InvalidItem(String shortCode, String reason) { }
}
