package com.svenruppert.urlshortener.api.utils;

import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingFilter;

import java.net.URLDecoder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class QueryUtils {

  private static final String EPOCH_MILLIS_REGEX = "\\d{10,}";
  private static final Map<String, UrlMappingFilter.SortBy> SORT_BY_LOOKUP
      = Map.of(
      "createdat", UrlMappingFilter.SortBy.CREATED_AT,
      "shortcode", UrlMappingFilter.SortBy.SHORT_CODE,
      "originalurl", UrlMappingFilter.SortBy.ORIGINAL_URL,
      "expiresat", UrlMappingFilter.SortBy.EXPIRES_AT
  );

  private QueryUtils() {
  }

  public static int parseIntOrDefault(String s, int def) {
    try {
      return (s == null || s.isBlank()) ? def : Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }

  public static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  public static Optional<UrlMappingFilter.SortBy> parseSort(String s) {
    if (s == null) return Optional.empty();
    String key = s.toLowerCase(Locale.ROOT);
    return Optional.ofNullable(SORT_BY_LOOKUP.get(key));
  }

  public static Optional<UrlMappingFilter.Direction> parseDir(String s) {
    if (s == null) return Optional.empty();
    return switch (s.toLowerCase(Locale.ROOT)) {
      case "asc" -> Optional.of(UrlMappingFilter.Direction.ASC);
      case "desc" -> Optional.of(UrlMappingFilter.Direction.DESC);
      default -> Optional.empty();
    };
  }

  public static String first(Map<String, List<String>> m, String k) {
    var list = m.get(k);
    return (list == null || list.isEmpty()) ? null : list.getFirst();
  }

  public static String urlDecode(String s) {
    return URLDecoder.decode(s, UTF_8);
  }

  /**
   * Parses a raw query string into a map where each key maps to a list of associated values.
   * Query parameters are extracted by splitting the input string on '&' characters
   * and further splitting each key-value pair on '='. Keys and values are URL-decoded.
   * If the input is null or blank, an empty map is returned.
   *
   * @param rawQuery the raw query string to parse, typically part of a URL after the '?' delimiter
   * @return a map where each key maps to a list of its associated values, or an empty map if
   *         the input is null or blank
   */
  public static Map<String, List<String>> parseQueryParams(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) return Collections.emptyMap();

    Map<String, List<String>> params = new LinkedHashMap<>();
    Arrays.stream(rawQuery.split("&"))
        .filter(pair -> !pair.isBlank())
        .map(pair -> pair.split("=", 2))
        .forEach(kv -> addParam(params, kv));
    return params;
  }

  private static void addParam(Map<String, List<String>> params, String[] kv) {
    String key = urlDecode(kv[0]);
    String value = kv.length > 1 ? urlDecode(kv[1]) : "";
    params.computeIfAbsent(key, __ -> new ArrayList<>()).add(value);
  }

  public static Optional<Boolean> parseBoolean(String raw) {
    return raw == null || raw.isBlank()
        ? Optional.empty()
        : Optional.of(Boolean.parseBoolean(raw));
  }

  /**
   * Parses a string input into an {@code Optional<Instant}, handling different formats such as
   * epoch milliseconds, ISO-8601 instant strings, or local date representations. If the input
   * is null, blank, or cannot be successfully parsed, an empty {@code Optional} is returned.
   *
   * @param input                the string to be parsed into an {@code Instant}
   * @param useStartOfDayForDate a flag indicating whether a date input should be resolved to
   *                             the start of the day ({@code true}) or end of the day ({@code false})
   * @return an {@code Optional} containing the parsed {@code Instant} if successful, or an
   * empty {@code Optional} if the input is null, blank, or unable to be parsed
   */
  public static Optional<Instant> parseInstant(String input, boolean useStartOfDayForDate) {
    if (input == null || input.isBlank()) return Optional.empty();

    return parseEpochMillis(input)
        .or(() -> parseIsoInstant(input))
        .or(() -> parseLocalDateInstant(input, useStartOfDayForDate));
  }

  /**
   * Parses a string representing epoch milliseconds into an {@code Optional<Instant>}.
   * If the input does not match the expected epoch milliseconds format or cannot
   * be parsed into a valid {@code Instant}, an empty {@code Optional} is returned.
   *
   * @param input the string representing epoch milliseconds to parse
   * @return an {@code Optional} containing the parsed {@code Instant} if successful,
   * or an empty {@code Optional} if the input is invalid or cannot be parsed
   */
  private static Optional<Instant> parseEpochMillis(String input) {
    if (!input.matches(EPOCH_MILLIS_REGEX)) return Optional.empty();
    try {
      return Optional.of(Instant.ofEpochMilli(Long.parseLong(input)));
    } catch (NumberFormatException ignore) {
      return Optional.empty();
    }
  }

  private static Optional<Instant> parseIsoInstant(String input) {
    try {
      return Optional.of(Instant.parse(input));
    } catch (DateTimeParseException ignore) {
      return Optional.empty();
    }
  }

  private static Optional<Instant> parseLocalDateInstant(String input, boolean useStartOfDayForDate) {
    try {
      LocalDate date = LocalDate.parse(input);
      return Optional.of(boundaryInstant(date, useStartOfDayForDate));
    } catch (DateTimeParseException ignore) {
      return Optional.empty();
    }
  }

  private static Instant boundaryInstant(LocalDate date, boolean startOfDay) {
    if (startOfDay) {
      return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
    return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
  }
}
