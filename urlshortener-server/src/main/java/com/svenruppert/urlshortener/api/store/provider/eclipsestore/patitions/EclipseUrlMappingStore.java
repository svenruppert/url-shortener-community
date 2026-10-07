package com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.api.store.urlmapping.MappingCreator;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.DataRoot;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.ToggleActive;
import org.eclipse.store.storage.types.StorageManager;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilterHelper.filterSortAndPage;
import static com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilterHelper.matches;
import static com.svenruppert.urlshortener.core.AliasPolicy.normalize;

public class EclipseUrlMappingStore
    implements UrlMappingStore, HasLogger {

  private final StorageManager storage;
  private final MappingCreator creator;

  public EclipseUrlMappingStore(StorageManager storage, Clock clock, ShortCodeGenerator generator) {
    this.storage = storage;

    var clockToUse = (clock == null) ? Clock.systemUTC() : clock;
    Function<MappingCreator.ErrorInfo, String> errorMapper = err -> JsonUtils.toJson(err.httpStatus(), err.message(), err.reasonCode());
    this.creator = new MappingCreator(
        generator,
        this::existsByCode,
        this::storeMappingAndPersist,
        clockToUse,
        errorMapper,
        code -> findByShortCode(code).map(ShortUrlMapping::originalUrl)
    );

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
    var existing = dataRoot().shortUrlMappings().get(shortCode);
    var keepHash = existing != null ? existing.getPasswordHash() : null;
    return editMapping(shortCode, url, expiredAt, active, keepHash);
  }

  @Override
  public Result<ShortUrlMapping, String> editMapping(String shortCode, String url, Instant expiredAt, Boolean active, String passwordHash) {
    logger().info("editMapping - shortCode: {} - originalUrl: {} - expiredAt: {} - active: {} - protected: {}", shortCode, url, expiredAt, active, passwordHash != null);
    var existsByCode = existsByCode(shortCode);
    if (existsByCode) {
      var urlMappings = dataRoot().shortUrlMappings();
      var shortUrlMappingOLD = urlMappings.get(shortCode);
      var originalOrNewUrl = url != null ? url : shortUrlMappingOLD.originalUrl();
      var originalOrNewActive = active != null ? active : shortUrlMappingOLD.active();
      var shortUrlMapping = new ShortUrlMapping(shortCode, originalOrNewUrl, shortUrlMappingOLD.createdAt(), expiredAt, originalOrNewActive, passwordHash);
      urlMappings.put(shortUrlMapping.shortCode(), shortUrlMapping);
      storage.store(dataRoot().shortUrlMappings());
      return Result.success(shortUrlMapping);
    } else {
      logger().info("editMapping - shortCode {} does not exist", shortCode);
      return Result.failure("editMapping - shortCode '" + shortCode + "' does not exist");
    }
  }

  @Override
  public boolean delete(String shortCode) {
    logger().info("Deleting shortCode '{}'", shortCode);
    var normalized = normalize(shortCode);
    logger().info("Normalized shortCode for deletion: '{}'", normalized);
    var removed = dataRoot().shortUrlMappings().remove(normalized) != null;
    logger().info("Mapping removed from store: {}", removed);
    if (removed) {
      storage.store(dataRoot().shortUrlMappings());
      logger().info("URL mapping changes persisted");

      // Cascade delete: remove all statistics for this shortCode
      dataRoot().removeStatisticsForShortCode(normalized);
      storage.store(dataRoot().redirectEvents());
      storage.store(dataRoot().hourlyAggregates());
      storage.store(dataRoot().dailyAggregates());
      logger().info("Statistics data removed for shortCode '{}'", normalized);
    }
    return removed;
  }

  @Override
  public Result<ToggleActive.ToggleActiveResponse, String> toggleActive(String shortCode, boolean newActiveValue) {
    if (shortCode == null || shortCode.isBlank())
      return Result.failure("shortCode '" + shortCode + "' is  valid");
    // Normalise like every other lookup in this store (delete, existsByCode,
    // findByShortCode all do). Without it an alias in a non-canonical spelling
    // reported "not found" here while find and delete resolved it fine.
    String normalized = normalize(shortCode);
    var urlMappings = dataRoot().shortUrlMappings();
    if (urlMappings.containsKey(normalized)) {
      var urlMapping = urlMappings.get(normalized);
      var updatedUrlMapping = urlMapping.withActive(newActiveValue);
      urlMappings.put(normalized, updatedUrlMapping);
      storage.store(dataRoot().shortUrlMappings());
      logger().info("toggleActive - changes persisted in store");
      return Result.success(new ToggleActive.ToggleActiveResponse(normalized, updatedUrlMapping.active()));
    } else {
      return Result.failure("shortCode " + shortCode + " not found");
    }
  }

  private DataRoot dataRoot() {
    return (DataRoot) storage.root();
  }

  @Override
  public boolean existsByCode(String shortCode) {
    logger().debug("existsByCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().debug("existsByCode - normalized for search '{}'", normalized);
    var containsKey = dataRoot().shortUrlMappings().containsKey(normalized);
    logger().debug("existsByCode - containsKey = {}", containsKey);
    return containsKey;
  }

  @Override
  public Optional<ShortUrlMapping> findByShortCode(String shortCode) {
    logger().debug("findByShortCode '{}'", shortCode);
    String normalized = normalize(shortCode);
    logger().debug("findByShortCode normalized for search '{}'", normalized);
    return Optional.ofNullable(dataRoot().shortUrlMappings().get(normalized));
  }

  @Override
  public List<ShortUrlMapping> findAll() {
    logger().info("findAll");
    return new ArrayList<>(dataRoot().shortUrlMappings().values());
  }

  @Override
  public List<ShortUrlMapping> find(UrlMappingFilter filter) {
    Objects.requireNonNull(filter, "filter");
    var valueStream = dataRoot().shortUrlMappings().values().stream();
    return filterSortAndPage(filter, valueStream);
  }

  @Override
  public int count(UrlMappingFilter filter) {
    int c = 0;
    for (ShortUrlMapping m : dataRoot().shortUrlMappings().values()) {
      if (matches(filter, m)) c++;
    }
    return c;
  }

  @Override
  public int countAll() {
    return dataRoot().shortUrlMappings().size();
  }
  /* ---------- internal ---------- */

  private void storeMappingAndPersist(ShortUrlMapping m) {
    var dataRoot = dataRoot();
    var mappings = dataRoot.shortUrlMappings();
    logger().info("storeMappingAndPersist - mappings size {}", mappings.size());
    mappings.put(m.shortCode(), m);
    logger().info("storeMappingAndPersist {}", m);
    var stored = storage.store(mappings);
    logger().info("storeMappingAndPersist stored - {}", stored);
  }
}
