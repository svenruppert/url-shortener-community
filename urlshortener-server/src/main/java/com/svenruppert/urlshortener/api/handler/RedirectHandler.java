package com.svenruppert.urlshortener.api.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.api.security.AppPasswordHashing;
import com.svenruppert.urlshortener.api.security.RateLimitGuard;
import com.svenruppert.urlshortener.api.store.statistics.RequestDataExtractor;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsWriter;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingLookup;
import com.svenruppert.urlshortener.api.utils.RequestBodyUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
    // HEAD must answer like GET without a body (link checkers probe this way).
    // POST is accepted only to submit a per-link password (the challenge form
    // posts back to the same path); any other method is rejected.
    final String method = exchange.getRequestMethod();
    final boolean headRequest = "HEAD".equalsIgnoreCase(method);
    final boolean getRequest = "GET".equalsIgnoreCase(method);
    final boolean postRequest = "POST".equalsIgnoreCase(method);
    if (!(getRequest || headRequest || postRequest)) {
      exchange.getResponseHeaders().set("Allow", "GET, HEAD, POST");
      exchange.sendResponseHeaders(HttpStatus.METHOD_NOT_ALLOWED.code(), -1);
      return;
    }

    // Per-IP rate limiting (P120); no-op unless a production/strict profile is active.
    if (!RateLimitGuard.INSTANCE.allow("redirect:" + RateLimitGuard.clientIp(exchange))) {
      logger().info("rate limit exceeded (redirect)");
      // RFC 9110 §10.2.3: say when to come back. Without it a bot retries at
      // once and keeps itself throttled — and this path serves end users and
      // crawlers, not only API clients.
      long retryAfter = RateLimitGuard.INSTANCE.retryAfterSeconds();
      if (retryAfter > 0) {
        exchange.getResponseHeaders().set("Retry-After", String.valueOf(retryAfter));
      }
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

    // Per-link password gate (V00.22.00). Checked after expiry (410) and active
    // (404) so those terminal answers keep precedence. Unlike expiry — which
    // ends in a flat 410 — a protected link answers with a challenge and only
    // redirects once the correct password arrives via POST.
    if (mapping.passwordHash().isPresent()) {
      final String storedHash = mapping.getPasswordHash();
      if (postRequest) {
        final String submitted = formField(exchange, "password");
        if (submitted != null
            && AppPasswordHashing.hasher().verify(submitted.toCharArray(), storedHash)) {
          // Correct password: count it exactly like a normal hit and redirect.
          recordRedirectEvent(exchange, code);
          exchange.getResponseHeaders().add("Location", mapping.originalUrl());
          exchange.sendResponseHeaders(HttpStatus.FOUND.code(), -1);
        } else {
          logger().info("password challenge failed for short code {}", code);
          sendPasswordForm(exchange, path, true);
        }
      } else if (headRequest) {
        // A link check cannot carry a password, so a protected link is "locked".
        exchange.sendResponseHeaders(HttpStatus.UNAUTHORIZED.code(), -1);
      } else {
        // GET on a protected link: show the challenge, do not count it as a visit.
        sendPasswordForm(exchange, path, false);
      }
      return;
    }

    // Unprotected link: unchanged behaviour. POST has no meaning here.
    if (postRequest) {
      exchange.getResponseHeaders().set("Allow", "GET, HEAD");
      exchange.sendResponseHeaders(HttpStatus.METHOD_NOT_ALLOWED.code(), -1);
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

  /**
   * Reads a single field from an {@code application/x-www-form-urlencoded} body.
   * Returns {@code null} when the field is absent.
   */
  private static String formField(HttpExchange exchange, String name)
      throws IOException {
    final String body = RequestBodyUtils.readBody(exchange.getRequestBody());
    if (body == null || body.isEmpty()) {
      return null;
    }
    for (String pair : body.split("&")) {
      final int eq = pair.indexOf('=');
      final String rawKey = eq >= 0 ? pair.substring(0, eq) : pair;
      final String rawValue = eq >= 0 ? pair.substring(eq + 1) : "";
      if (name.equals(URLDecoder.decode(rawKey, StandardCharsets.UTF_8))) {
        return URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private static final String FORM_TEMPLATE = """
      <!doctype html>
      <html lang="de">
      <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Passwort erforderlich</title>
      <style>
        body{font-family:system-ui,-apple-system,sans-serif;background:#f5f5f7;display:flex;min-height:100vh;align-items:center;justify-content:center;margin:0}
        form{background:#fff;padding:2rem;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.08);max-width:20rem;width:90%}
        h1{font-size:1.1rem;margin:0 0 1rem}
        input{width:100%;box-sizing:border-box;padding:.6rem;border:1px solid #ccc;border-radius:8px;font-size:1rem}
        button{margin-top:1rem;width:100%;padding:.6rem;border:0;border-radius:8px;background:#2563eb;color:#fff;font-size:1rem;cursor:pointer}
        .error{color:#b00020;font-size:.9rem;margin:0 0 1rem}
      </style>
      </head>
      <body>
      <form method="post" action="{{ACTION}}">
      <h1>Dieser Link ist passwortgeschützt</h1>
      {{ERROR}}
      <input type="password" name="password" autofocus autocomplete="current-password" aria-label="Passwort">
      <button type="submit">Weiter</button>
      </form>
      </body>
      </html>
      """;

  /**
   * Sends the 401 password-challenge page. {@code error} adds a "wrong password"
   * line after a failed attempt. The page is user-facing content (German, the
   * product language); the redirect server has no i18n provider of its own.
   */
  private void sendPasswordForm(HttpExchange exchange, String path, boolean error)
      throws IOException {
    final String errorBlock = error
        ? "<p class=\"error\">Falsches Passwort. Bitte erneut versuchen.</p>"
        : "";
    final String html = FORM_TEMPLATE
        .replace("{{ACTION}}", htmlAttribute(path))
        .replace("{{ERROR}}", errorBlock);
    final byte[] body = html.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_HTML.withCharsetUtf8());
    exchange.sendResponseHeaders(HttpStatus.UNAUTHORIZED.code(), body.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(body);
    }
  }

  private static String htmlAttribute(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}
