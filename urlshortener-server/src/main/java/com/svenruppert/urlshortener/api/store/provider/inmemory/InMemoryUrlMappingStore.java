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
import java.time.Clock;

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
        Clock.systemUTC(),
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
    // Normalise like every other lookup in this store (delete, existsByCode,
    // findByShortCode all do). Without it an alias in a non-canonical spelling
    // reported "not found" here while find and delete resolved it fine.
    String normalized = normalize(shortCode);
    if (store.containsKey(normalized)) {
      var urlMapping = store.get(normalized);
      var updatedUrlMapping = urlMapping.withActive(newActiveValue);
      store.put(normalized, updatedUrlMapping);
      return Result.success(new ToggleActiveResponse(normalized, updatedUrlMapping.active()));
    } else {
      return Result.failure("shortCode " + shortCode + " not found");
    }
  }

  @Override
  public Result<ShortUrlMapping, String> createMapping(Instant createdAt, String shortCode, String originalUrl, Instant expiredAt, Boolean active) {
    return createMapping(createdAt, shortCode, originalUrl, expiredAt, active, null);
  }

  @Override
  public Result<ShortUrlMapping, String> createMapping(Instant createdAt, String shortCode, String originalUrl, Instant expiredAt, Boolean active, String passwordHash) {
    logger().info("createMapping - createdAt: {} - shortCode: {} - originalUrl: {} - expiredAt: {} - active: {} - protected: {}", createdAt, shortCode, originalUrl, expiredAt, active, passwordHash != null);
    var originalOrDefaultActive = active != null ? active : true;
    return creator.create(createdAt, shortCode, originalUrl, expiredAt, originalOrDefaultActive, passwordHash);
  }

  @Override
  public Result<ShortUrlMapping, String> createMapping(String shortCode, String originalUrl, Instant expiredAt, Boolean active) {
    return createMapping(shortCode, originalUrl, expiredAt, active, null);
  }

  @Override
  public Result<ShortUrlMapping, String> createMapping(String shortCode, String originalUrl, Instant expiredAt, Boolean active, String passwordHash) {
    logger().info("createMapping - shortCode: {} - originalUrl: {} - expiredAt: {} - active: {} - protected: {}", shortCode, originalUrl, expiredAt, active, passwordHash != null);
    var originalOrDefaultActive = active != null ? active : true;
    return creator.create(shortCode, originalUrl, expiredAt, originalOrDefaultActive, passwordHash);
  }

  @Override
  public Result<ShortUrlMapping, String> editMapping(String shortCode, String url, Instant expiredAt, Boolean active) {
    // Plain edit preserves any existing password protection (null-param = keep).
    var existing = store.get(shortCode);
    var keepHash = existing != null ? existing.getPasswordHash() : null;
    return editMapping(shortCode, url, expiredAt, active, keepHash);
  }

  @Override
  public Result<ShortUrlMapping, String> editMapping(String shortCode, String url, Instant expiredAt, Boolean active, String passwordHash) {
    logger().info("editMapping - shortCode: {} - originalUrl: {} - expiredAt: {} - active: {} - protected: {}", shortCode, url, expiredAt, active, passwordHash != null);
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
          originalOrNewActive,
          passwordHash);
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
