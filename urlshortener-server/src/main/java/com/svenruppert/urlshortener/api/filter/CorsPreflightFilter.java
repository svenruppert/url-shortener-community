package com.svenruppert.urlshortener.api.filter;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;

import java.io.IOException;

/**
 * CORS with a strict origin allow-list (P230), replacing the blanket
 * preflight blocker. A browser request whose {@code Origin} is in
 * {@link CorsConfig} gets the appropriate CORS headers (and a 204 for
 * preflight); any other cross-origin (browser) preflight is still blocked
 * with 403. Same-origin / non-browser calls (no {@code Origin}) pass through.
 */
public class CorsPreflightFilter
    extends Filter
    implements HasLogger {

  @Override
  public String description() {
    return "CORS allow-list: permits configured origins, blocks other browser preflight";
  }

  @Override
  public void doFilter(HttpExchange exchange, Chain chain)
      throws IOException {
    String origin = exchange.getRequestHeaders().getFirst("Origin");
    boolean preflight = "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod()) && origin != null;

    if (origin != null && CorsConfig.isAllowed(origin)) {
      Headers h = exchange.getResponseHeaders();
      h.set("Access-Control-Allow-Origin", origin);
      h.set("Vary", "Origin");
      h.set("Access-Control-Allow-Credentials", "true");
      if (preflight) {
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-API-Key");
        h.set("Access-Control-Max-Age", "600");
        exchange.sendResponseHeaders(HttpStatus.NO_CONTENT.code(), -1);
        exchange.close();
        return;
      }
      chain.doFilter(exchange);
      return;
    }

    if (preflight) {
      logger().info("blocking preflight from disallowed origin {}", origin);
      exchange.sendResponseHeaders(HttpStatus.FORBIDDEN.code(), -1);
      exchange.close();
      return;
    }
    chain.doFilter(exchange);
  }
}
