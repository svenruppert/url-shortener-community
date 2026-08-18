package com.svenruppert.urlshortener.api.filter;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * Global security response headers (SEC-M4, V00.17.00 P080) — one filter on
 * every context of both HTTP servers instead of per-handler copies:
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — no MIME sniffing.</li>
 *   <li>{@code X-Frame-Options: DENY} + CSP {@code frame-ancestors 'none'} —
 *       no framing of API or redirect responses (clickjacking).</li>
 *   <li>{@code Content-Security-Policy: default-src 'none'} — API responses
 *       never execute active content even if rendered by a browser.</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}.</li>
 * </ul>
 *
 * <p>HSTS is deliberately NOT set here: the servers speak plain HTTP behind a
 * TLS-terminating proxy (Caddy), which owns Strict-Transport-Security — see
 * docs/deployment/debian-caddy.md.
 */
public class SecurityHeadersFilter
    extends Filter {

  @Override
  public String description() {
    return "adds the global security response headers (nosniff, frame denial, CSP, referrer policy)";
  }

  @Override
  public void doFilter(HttpExchange exchange, Chain chain)
      throws IOException {
    Headers h = exchange.getResponseHeaders();
    h.set("X-Content-Type-Options", "nosniff");
    h.set("X-Frame-Options", "DENY");
    h.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    h.set("Referrer-Policy", "strict-origin-when-cross-origin");
    chain.doFilter(exchange);
  }
}
