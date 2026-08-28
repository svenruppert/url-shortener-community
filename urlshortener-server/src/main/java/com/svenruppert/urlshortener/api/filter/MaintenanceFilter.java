package com.svenruppert.urlshortener.api.filter;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.api.extension.MaintenanceGate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Rejects admin requests with 503 while the {@link MaintenanceGate} is
 * closed (V00.18.00 P090) — the request that closed the gate already passed
 * this filter and keeps running. The public redirect server does not carry
 * this filter.
 */
public final class MaintenanceFilter
    extends Filter {

  private static final byte[] BODY =
      "{\"error\":\"maintenance\",\"message\":\"server is taking a consistent snapshot — retry shortly\"}"
          .getBytes(StandardCharsets.UTF_8);

  @Override
  public void doFilter(HttpExchange exchange, Chain chain)
      throws IOException {
    if (MaintenanceGate.isClosed()) {
      exchange.getResponseHeaders().add("Retry-After", "5");
      exchange.sendResponseHeaders(HttpStatus.SERVICE_UNAVAILABLE.code(), BODY.length);
      exchange.getResponseBody().write(BODY);
      exchange.close();
      return;
    }
    chain.doFilter(exchange);
  }

  @Override
  public String description() {
    return "503 while the maintenance gate is closed (consistent snapshot window)";
  }
}
