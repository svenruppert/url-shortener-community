package com.svenruppert.urlshortener.api.utils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ImportListQueryUtils {

  private ImportListQueryUtils() {
  }

  public static Map<String, String> parseQueryParamsSingle(String rawQuery) {
    Map<String, String> params = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isBlank()) return params;

    for (String pair : rawQuery.split("&")) {
      if (pair.isEmpty()) continue;

      int idx = pair.indexOf('=');
      String key = idx >= 0 ? pair.substring(0, idx) : pair;
      String val = idx >= 0 ? pair.substring(idx + 1) : "";

      key = urlDecode(key);
      val = urlDecode(val);

      // "single-value": last one wins (konsequent, einfach)
      params.put(key, val);
    }
    return params;
  }

  public static int parseInt(String value, int def) {
    if (value == null || value.isBlank()) return def;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return def;
    }
  }

  public static int clampInt(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

//  public static Paging pageFrom(Map<String, String> q,
//                                int defaultPage,
//                                int defaultSize,
//                                int minSize,
//                                int maxSize) {
//    int page = parseInt(q.get("page"), defaultPage);
//    int size = parseInt(q.get("size"), defaultSize);
//
//    page = Math.max(0, page);
//    size = clampInt(size, minSize, maxSize);
//
//    return new Paging(page, size);
//  }

  public static Slice slice(int page, int size, int total) {
    if (total <= 0) return new Slice(0, 0);

    long startL = (long) page * (long) size;
    if (startL >= total) {
      // Seite außerhalb: leere Slice
      return new Slice(total, total);
    }

    int start = (int) startL;
    int end = Math.min(total, start + size);
    return new Slice(start, end);
  }

  private static String urlDecode(String s) {
    return URLDecoder.decode(s, StandardCharsets.UTF_8);
  }

  public record Paging(int page, int size) { }
  public record Slice(int startInclusive, int endExclusive) { }
}
