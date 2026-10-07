package com.svenruppert.urlshortener.api.store.urlmapping;

import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ToggleActive.ToggleActiveResponse;

import java.time.Instant;

public interface UrlMappingUpdater {


  Result<ShortUrlMapping, String> createMapping(Instant createdAt, String shortCode, String originalUrl, Instant expiredAt, Boolean active);

  Result<ShortUrlMapping, String> createMapping(String shortCode, String originalUrl, Instant expiredAt, Boolean active);

  /**
   * Create variant carrying an already-hashed per-link password ({@code null} = no
   * password protection). Mirrors the expiry seam; the hash is opaque to the store.
   */
  Result<ShortUrlMapping, String> createMapping(Instant createdAt, String shortCode, String originalUrl, Instant expiredAt, Boolean active, String passwordHash);

  Result<ShortUrlMapping, String> createMapping(String shortCode, String originalUrl, Instant expiredAt, Boolean active, String passwordHash);

  Result<ShortUrlMapping, String> editMapping(String alias, String url, Instant expiredAt, Boolean active);

  /**
   * Edit variant that writes the given {@code passwordHash} straight through, exactly
   * like {@code expiredAt}: {@code null} removes password protection. The caller
   * (handler) is responsible for the keep/clear/replace resolution before calling.
   */
  Result<ShortUrlMapping, String> editMapping(String alias, String url, Instant expiredAt, Boolean active, String passwordHash);

  boolean delete(String shortCode);

  Result<ToggleActiveResponse, String> toggleActive(String shortCode, boolean newActiveValue);
}
