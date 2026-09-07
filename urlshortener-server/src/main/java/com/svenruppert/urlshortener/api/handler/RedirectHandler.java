package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.api.security.RateLimitGuard;
import com.svenruppert.urlshortener.api.store.statistics.RequestDataExtractor;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsWriter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingLookup;
import com.svenruppert.urlshortener.api.utils.RequestMethodUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.time.Instant;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_REDIRECT;

public class RedirectHandler
    implements HttpHandler, HasLogger {

  private final UrlMappingLookup store;
  private final StatisticsWriter statisticsWriter;
  private final RequestDataExtractor requestDataExtractor;

  public RedirectHandler(UrlMappingLookup store, StatisticsWriter statisticsWriter) {
    this.store = store;
    this.statisticsWriter = statisticsWriter;
    this.requestDataExtractor = new RequestDataExtractor();
  }

  /**
   * Constructor for backwards compatibility (without statistics).
   */
  public RedirectHandler(UrlMappingLookup store) {
    this(store, null);
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    // HEAD must answer exactly like GET, only without a body: link checkers
    // (Microsoft SafeLinks, Teams previews, WordPress) probe short links this
    // way and treat anything but a redirect as a broken link. Every response
    // below already sends -1 as its length, so no branch writes a body.
    if (!RequestMethodUtils.requireGetOrHead(exchange)) return;
    final boolean headRequest = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());

    // Per-IP rate limiting (P120); no-op unless a production/strict profile is active.
    if (!RateLimitGuard.INSTANCE.allow("redirect:" + RateLimitGuard.clientIp(exchange))) {
      logger().info("rate limit exceeded (redirect)");
      exchange.sendResponseHeaders(HttpStatus.TOO_MANY_REQUESTS.code(), -1);
      return;
    }

    final String path = exchange.getRequestURI().getPath();
    if (path == null || !path.startsWith(PATH_REDIRECT)) {
      exchange.sendResponseHeaders(HttpStatus.BAD_REQUEST.code(), -1);
      return;
    }
    // The bare origin carries no short code. That is a well-formed request for
    // something that does not exist, so it is a 404 — not a 400. Browsers,
    // Googlebot and GPTBot all reach this path; a malformed-request answer to a
    // perfectly formed request misreports the problem. Serving a landing page
    // here would need a configured UI address the server does not have.
    final String code = path.substring((PATH_REDIRECT).length());
    if (code.isBlank()) {
      exchange.sendResponseHeaders(HttpStatus.NOT_FOUND.code(), -1);
      return;
    }
    // A shortcode is a single path segment. Multi-segment paths like
    // /VAADIN/push or /sw.js/... are framework/asset noise from a browser
    // tab pointed at the redirect origin — reject quietly without touching
    // the store (avoids per-request lookups and log spam on the hot path).
    if (code.indexOf('/') >= 0) {
      logger().debug("Ignoring non-shortcode path {}", code);
      exchange.sendResponseHeaders(HttpStatus.NOT_FOUND.code(), -1);
      return;
    }
    logger().debug("Looking for short code {}", code);
    var mappingOpt = store.findByShortCode(code);

    if (mappingOpt.isEmpty()) {
      logger().debug("No mapping found for short code {}", code);
      exchange.sendResponseHeaders(HttpStatus.NOT_FOUND.code(), -1);
      return;
    }

    var mapping = mappingOpt.get();
    if (isExpired(mapping)) {
      logger().info("Short code {} is expired at {}", code, mapping.expiresAt().orElse(null));
      exchange.sendResponseHeaders(HttpStatus.GONE.code(), -1);
      return;
    }

    if (!mapping.active()) {
      logger().info("Short code {} is inactive", code);
      exchange.sendResponseHeaders(HttpStatus.NOT_FOUND.code(), -1);
      return;
    }

    // Record the redirect event (non-blocking). A HEAD request is a link check,
    // not a visit — counting it would let every SafeLinks or Teams probe inflate
    // the click figures the statistics and analytics views are built on.
    if (!headRequest) {
      recordRedirectEvent(exchange, code);
    }

    exchange.getResponseHeaders().add("Location", mapping.originalUrl());
    exchange.sendResponseHeaders(HttpStatus.FOUND.code(), -1);
  }

  private void recordRedirectEvent(HttpExchange exchange, String shortCode) {
    if (statisticsWriter == null) {
      return;
    }
    try {
      var event = requestDataExtractor.extractEvent(exchange, shortCode);
      statisticsWriter.recordEvent(event);
      logger().debug("Recorded redirect event for shortCode={}", shortCode);
    } catch (Exception e) {
      // Never let statistics recording fail the redirect
      logger().warn("Failed to record redirect event for shortCode={}", shortCode, e);
    }
  }

  private boolean isExpired(ShortUrlMapping mapping) {
    return mapping.expiresAt()
        .map(exp -> exp.isBefore(Instant.now()))
        .orElse(false);
  }
}