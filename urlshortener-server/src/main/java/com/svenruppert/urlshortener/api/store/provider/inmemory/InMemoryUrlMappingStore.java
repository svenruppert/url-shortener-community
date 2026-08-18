package com.svenruppert.urlshortener.api.store.provider.inmemory;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.urlmapping.MappingCreator;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ToggleActive.ToggleActiveResponse;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilterHelper.filterSortAndPage;
import static com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilterHelper.matches;
import static com.svenruppert.urlshortener.core.AliasPolicy.normalize;
import static com.svenruppert.urlshortener.core.JsonUtils.toJson;

public class InMemoryUrlMappingStore
    implements UrlMappingStore, HasLogger {

  private final Map<String, ShortUrlMapping> store = new ConcurrentHashMap<>();

  private final MappingCreator creator;

  public InMemoryUrlMappingStore(ShortCodeGenerator generator) {
    this.creator = new MappingCreator(
        generator,
        this::existsByCode,
        this::storeMapping,
        java.time.Clock.systemUTC(),
        err -> toJson(err.httpStatus(), err.message(), err.reasonCode()),
        code -> findByShortCode(code).map(ShortUrlMapping::originalUrl)
    );
  }

  @Override
  public Optional<ShortUrlMapping> findByShortCode(String shortCode) {
    logger().debug("findByShortCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().debug("findByShortCode normalized for search '{}'", normalized);
    return Optional.ofNullable(store.get(normalized));
  }

  @Override
  public List<ShortUrlMapping> findAll() {
    return new ArrayList<>(store.values());
  }

  @Override
  public List<ShortUrlMapping> find(UrlMappingFilter filter) {
    Objects.requireNonNull(filter, "filter");
    var valueStream = store.values().stream();
    return filterSortAndPage(filter, valueStream);
  }

  @Override
  public int count(UrlMappingFilter filter) {
    int c = 0;
    for (ShortUrlMapping m : store.values()) {
      if (matches(filter, m)) c++;
    }
    return c;
  }

  @Override
  public int countAll() {
    return store.size();
  }

  @Override
  public boolean delete(String shortCode) {
    logger().info("delete '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().info("delete - normalized for deletion '{}'", normalized);
    return store.remove(normalized) != null;
  }

  @Override
  public Result<ToggleActiveResponse, String> toggleActive(String shortCode, boolean newActiveValue) {
    if (shortCode == null || shortCode.isBlank())
      return Result.failure("shortCode '" + shortCode + "' is  valid");
    if (store.containsKey(shortCode)) {
      var urlMapping = store.get(shortCode);
      var updatedUrlMapping = urlMapping.withActive(newActiveValue);
      store.put(shortCode, updatedUrlMapping);
      return Result.success(new ToggleActiveResponse(shortCode, updatedUrlMapping.active()));
    } else {
      return Result.failure("shortCode " + shortCode + " not found");
    }
  }

  @Override
  public Result<ShortUrlMapping, String> createMapping(Instant createdAt, String shortCode, String originalUrl, Instant expiredAt, Boolean active) {
    logger().info("createMapping - createdAt: {} - shortCode: {} - originalUrl: {} - expiredAt: {} - active: {}", createdAt, shortCode, originalUrl, expiredAt, active);
    var originalOrDefaultActive = active != null ? active : true;
    return creator.create(createdAt, shortCode, originalUrl, expiredAt, originalOrDefaultActive);
  }

  @Override
  public Result<ShortUrlMapping, String> createMapping(String shortCode, String originalUrl, Instant expiredAt, Boolean active) {
    logger().info("createMapping - shortCode: {} - originalUrl: {} - expiredAt: {} - active: {}", shortCode, originalUrl, expiredAt, active);
    var originalOrDefaultActive = active != null ? active : true;
    return creator.create(shortCode, originalUrl, expiredAt, originalOrDefaultActive);
  }

  @Override
  public Result<ShortUrlMapping, String> editMapping(String shortCode, String url, Instant expiredAt, Boolean active) {
    logger().info("editMapping - shortCode: {} - originalUrl: {} - expiredAt: {} - active: {}", shortCode, url, expiredAt, active);
    var existsByCode = existsByCode(shortCode);
    if (existsByCode) {
      var shortUrlMappingOLD = store.get(shortCode);
      var originalOrNewUrl = url != null ? url : shortUrlMappingOLD.originalUrl();
      var originalOrNewActive = active != null ? active : shortUrlMappingOLD.active();
      var shortUrlMapping = new ShortUrlMapping(
          shortCode,
          originalOrNewUrl,
          shortUrlMappingOLD.createdAt(),
          expiredAt,
          originalOrNewActive);
      store.put(shortUrlMapping.shortCode(), shortUrlMapping);
      return Result.success(shortUrlMapping);
    } else {
      logger().info("editMapping - shortCode {} does not exists", shortCode);
      return Result.failure("editMapping - shortCode '" + shortCode + "' does not exists");
    }
  }

  private void storeMapping(ShortUrlMapping shortMapping) {
    store.put(shortMapping.shortCode(), shortMapping);
  }

  @Override
  public boolean existsByCode(String shortCode) {
    logger().debug("existsByCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().debug("existsByCode - normalized for search '{}'", normalized);
    return store.containsKey(normalized);
  }
}