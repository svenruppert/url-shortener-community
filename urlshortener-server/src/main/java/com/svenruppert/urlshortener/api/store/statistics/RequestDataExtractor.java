package com.svenruppert.urlshortener.api.store.statistics;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import com.svenruppert.urlshortener.core.statistics.RedirectEventBuilder;

import java.time.Clock;
import java.util.List;

/**
 * Extracts redirect event data from an HTTP request.
 * Centralizes the logic for reading headers and extracting client information.
 */
public final class RequestDataExtractor {

  private static final String HEADER_USER_AGENT = "User-Agent";
  private static final String HEADER_REFERER = "Referer";
  private static final String HEADER_ACCEPT_LANGUAGE = "Accept-Language";
  private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

  private final Clock clock;

  public RequestDataExtractor(Clock clock) {
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  public RequestDataExtractor() {
    this(Clock.systemUTC());
  }

  /**
   * Creates a RedirectEvent from an HttpExchange.
   *
   * @param exchange  the HTTP exchange
   * @param shortCode the short code being resolved
   * @return the redirect event
   */
  public RedirectEvent extractEvent(HttpExchange exchange, String shortCode) {
    var headers = exchange.getRequestHeaders();

    return RedirectEventBuilder.forShortCode(shortCode, clock)
        .userAgent(getFirstHeader(headers, HEADER_USER_AGENT))
        .referer(getFirstHeader(headers, HEADER_REFERER))
        .acceptLanguage(getFirstHeader(headers, HEADER_ACCEPT_LANGUAGE))
        .ipAddress(extractClientIp(exchange))
        .build();
  }

  /**
   * Extracts the client IP address, considering proxy headers.
   */
  private String extractClientIp(HttpExchange exchange) {
    var headers = exchange.getRequestHeaders();

    // Check X-Forwarded-For first (common proxy header)
    String forwardedFor = getFirstHeader(headers, HEADER_X_FORWARDED_FOR);
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      // X-Forwarded-For can contain multiple IPs, take the first one
      int commaIndex = forwardedFor.indexOf(',');
      if (commaIndex > 0) {
        return forwardedFor.substring(0, commaIndex).trim();
      }
      return forwardedFor.trim();
    }

    // Fall back to remote address
    var remoteAddress = exchange.getRemoteAddress();
    if (remoteAddress != null && remoteAddress.getAddress() != null) {
      return remoteAddress.getAddress().getHostAddress();
    }

    return null;
  }

  private String getFirstHeader(com.sun.net.httpserver.Headers headers, String name) {
    List<String> values = headers.get(name);
    if (values != null && !values.isEmpty()) {
      return values.getFirst();
    }
    return null;
  }
}
