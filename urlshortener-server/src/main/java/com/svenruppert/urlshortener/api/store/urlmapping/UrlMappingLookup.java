package com.svenruppert.urlshortener.api.store.urlmapping;

import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.util.List;
import java.util.Optional;

public interface UrlMappingLookup {

  boolean existsByCode(String shortCode);

  Optional<ShortUrlMapping> findByShortCode(String shortCode);
  List<ShortUrlMapping> findAll();
  List<ShortUrlMapping> find(UrlMappingFilter filter);

  int count(UrlMappingFilter filter);
  int countAll();
}