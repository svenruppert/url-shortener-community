package com.svenruppert.urlshortener.api.store.urlmapping;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import com.svenruppert.urlshortener.api.ShortCodeGenerator;
import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.core.RedirectCycleDetector;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static com.svenruppert.urlshortener.core.AliasPolicy.normalize;
import static com.svenruppert.urlshortener.core.DefaultValues.SHORTCODE_BASE_URL;
import static com.svenruppert.urlshortener.core.StringUtils.isNullOrBlank;

/**
 * Reusable, memory-agnostic generation of a ShortUrlMapping.
 */
public final class MappingCreator
    implements HasLogger {

  private final ShortCodeGenerator generator;
  private final ExistsByCode exists;
  private final PutMapping store;
  private final Clock clock;
  private final Function<ErrorInfo, String> errorMapper; // maps ErrorInfo → e.g. your toJson(...)
  private final Function<String, Optional<String>> targetResolver; // normalized code → existing target URL

  public MappingCreator(ShortCodeGenerator generator,
                        ExistsByCode exists,
                        PutMapping store,
                        Clock clock,
                        Function<ErrorInfo, String> errorMapper) {
    this(generator, exists, store, clock, errorMapper, c -> Optional.empty());
  }

  public MappingCreator(ShortCodeGenerator generator,
                        ExistsByCode exists,
                        PutMapping store,
                        Clock clock,
                        Function<ErrorInfo, String> errorMapper,
                        Function<String, Optional<String>> targetResolver) {
    this.generator = Objects.requireNonNull(generator);
    this.exists = Objects.requireNonNull(exists);
    this.store = Objects.requireNonNull(store);
    this.clock = Objects.requireNonNullElse(clock, Clock.systemUTC());
    this.errorMapper = Objects.requireNonNull(errorMapper);
    this.targetResolver = Objects.requireNonNullElse(targetResolver, c -> Optional.empty());
  }

  public Result<ShortUrlMapping, String> create(Instant createdAt, String alias, String url, Instant expiredAt, Boolean active) {
    return create(createdAt, alias, url, expiredAt, active, null);
  }

  public Result<ShortUrlMapping, String> create(Instant createdAt, String alias, String url, Instant expiredAt, Boolean active, String passwordHash) {
    logger().info("create - createdAt='{}' alias='{}' / url='{}' / expiredAt='{}' / protected={}", createdAt, alias, url, expiredAt, passwordHash != null);
    final String shortCode;
    if (!isNullOrBlank(alias)) {
      var aliasCheck = AliasPolicy.validate(alias);
      if (aliasCheck.failed()) {
        var reason = aliasCheck.reason();
        var reasonCode = switch (reason) {
          case NULL_OR_BLANK -> "ALIAS_EMPTY";
          case TOO_SHORT -> "ALIAS_TOO_SHORT";
          case TOO_LONG -> "ALIAS_TOO_LONG";
          case INVALID_CHARS -> "ALIAS_INVALID_CHARS";
          case RESERVED -> "ALIAS_RESERVED";
        };
        var errorJson = errorMapper.apply(new ErrorInfo("400", reason.defaultMessage, reasonCode));
        logger().warn("aliasCheck.failed() - createMapping - {}", errorJson);
        return Result.failure(errorJson);
      }
      var normalized = normalize(alias);
      if (exists.test(normalized)) {
        var errorJson = errorMapper.apply(new ErrorInfo("409", "normalizedAlias already in use", "ALIAS_CONFLICT"));
        logger().warn("exists.test(normalized) - createMapping - {}", errorJson);
        return Result.failure(errorJson);
      }
      shortCode = normalized;
    } else {
      logger().info("alias is null or blank");
      String gen = normalize(generator.nextCode());
      logger().info("next normalized alias .. {} ", gen);
      while (exists.test(gen)) {
        gen = normalize(generator.nextCode());
        logger().info("next normalized alias .. {} ", gen);
      }
      shortCode = gen;
    }

    if (RedirectCycleDetector.formsCycle(SHORTCODE_BASE_URL, targetResolver, shortCode, url)) {
      var errorJson = errorMapper.apply(new ErrorInfo("409", "redirect target forms a cycle", "REDIRECT_CYCLE"));
      logger().warn("redirect cycle detected - createMapping - shortCode='{}' url='{}' - {}", shortCode, url, errorJson);
      return Result.failure(errorJson);
    }

    var mapping = new ShortUrlMapping(shortCode, url, createdAt, expiredAt, active, passwordHash);
    logger().info("mapping to store .. {}", mapping);
    store.accept(mapping);
    logger().info("mapping stored .. {}", mapping);
    return Result.success(mapping);
  }

  /**
   * Main method: creates mapping with optional alias.
   */
  public Result<ShortUrlMapping, String> create(String alias, String url, Instant expiredAt, Boolean active) {
    return create(alias, url, expiredAt, active, null);
  }

  public Result<ShortUrlMapping, String> create(String alias, String url, Instant expiredAt, Boolean active, String passwordHash) {
    logger().info("createMapping - alias='{}' / url='{}' / expiredAt='{}' / protected={}", alias, url, expiredAt, passwordHash != null);
    var createdAt = Instant.now(clock);
    return create(createdAt, alias, url, expiredAt, active, passwordHash);
  }

  @FunctionalInterface
  public interface ExistsByCode {
    boolean test(String code);
  }

  @FunctionalInterface
  public interface PutMapping {
    void accept(ShortUrlMapping m);
  }

  /**
   * Error information that the caller (e.g. HTTP layer) can freely map into JSON/logs.
   */
  public record ErrorInfo(String httpStatus, String message, String reasonCode) { }

}
