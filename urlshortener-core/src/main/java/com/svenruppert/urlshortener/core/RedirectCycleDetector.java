package com.svenruppert.urlshortener.core;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Detects redirect cycles (A→B→A, A→A, longer loops) before a mapping is
 * stored (P060). A target URL that points back at this shortener's base
 * ({@link DefaultValues#SHORTCODE_BASE_URL}) references another short code;
 * following that chain must not loop back to the new code.
 *
 * <p>Pure/stateless — the mapping store is injected as a resolver
 * {@code code -> Optional<targetUrl>}, so this is unit-testable without a store.
 */
public final class RedirectCycleDetector {

  private static final int MAX_DEPTH = 50;

  private RedirectCycleDetector() {
  }

  /**
   * @param baseUrl        the public short-code base (e.g. {@code https://3g3.eu/})
   * @param targetResolver existing mapping lookup: normalized code → its target URL
   * @param newCode        the short code about to be created/edited
   * @param newTarget      the target URL of the new/edited mapping
   * @return {@code true} if following the chain forms a cycle
   */
  public static boolean formsCycle(String baseUrl,
                                   Function<String, Optional<String>> targetResolver,
                                   String newCode,
                                   String newTarget) {
    if (baseUrl == null || newCode == null || targetResolver == null) return false;

    Set<String> seen = new HashSet<>();
    seen.add(AliasPolicy.normalize(newCode));

    String current = extractCode(baseUrl, newTarget);
    int depth = 0;
    while (current != null && depth++ < MAX_DEPTH) {
      String norm = AliasPolicy.normalize(current);
      if (seen.contains(norm)) return true;            // loops back → cycle
      seen.add(norm);
      Optional<String> next = targetResolver.apply(norm);
      if (next == null || next.isEmpty()) return false; // dangling → no cycle
      current = extractCode(baseUrl, next.get());
    }
    return false;
  }

  /**
   * Extracts the single short-code segment from a URL pointing at {@code baseUrl},
   * or {@code null} if the URL does not reference a plain short code on this host.
   */
  public static String extractCode(String baseUrl, String url) {
    if (url == null || baseUrl == null) return null;
    String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    if (!url.startsWith(base)) return null;
    String rest = url.substring(base.length());
    int cut = rest.length();
    int q = rest.indexOf('?');
    if (q >= 0) cut = Math.min(cut, q);
    int h = rest.indexOf('#');
    if (h >= 0) cut = Math.min(cut, h);
    rest = rest.substring(0, cut);
    if (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
    if (rest.isBlank() || rest.contains("/")) return null; // not a single segment
    return rest;
  }
}
