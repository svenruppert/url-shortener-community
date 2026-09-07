package com.svenruppert.urlshortener.api.filter;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Last line of defence: guarantees that every request gets an HTTP response.
 *
 * <p>A {@link RuntimeException} escaping a handler is not answered by the JDK
 * {@code HttpServer} — it closes the connection without sending a status line,
 * and the client sees {@code "header parser received no bytes"} instead of an
 * error it can act on. That was reproducible on the public endpoints
 * {@code POST /api/refresh} and {@code POST /api/password-reset/request}: Jackson 3
 * reports parse failures as unchecked {@code JacksonException}, so a malformed
 * body escaped handlers that caught only {@code IOException}.
 *
 * <p>The individual parse sites answer 400 themselves (see
 * {@code RequestBodyUtils.jsonMapOrBadRequest}); this filter exists so that a
 * defect nobody anticipated still produces a response rather than a dropped
 * connection. It never overwrites a response that is already on the wire —
 * {@code getResponseCode() == -1} tells us nothing has been sent yet.
 */
public final class FailSafeFilter
    extends Filter
    implements HasLogger {

  private static final byte[] BODY =
      "{\"error\":\"internal_error\"}".getBytes(StandardCharsets.UTF_8);

  @Override
  public void doFilter(HttpExchange exchange, Chain chain)
      throws IOException {
    try {
      chain.doFilter(exchange);
    } catch (RuntimeException escaped) {
      logger().error("unhandled {} in {} {}",
                     escaped.getClass().getName(),
                     exchange.getRequestMethod(),
                     exchange.getRequestURI().getPath(),
                     escaped);
      respondIfNothingSentYet(exchange);
    }
  }

  private void respondIfNothingSentYet(HttpExchange exchange)
      throws IOException {
    try {
      if (exchange.getResponseCode() == -1) {
        // A HEAD response carries the status and headers but never a body.
        boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(HttpStatus.INTERNAL_SERVER_ERROR.code(),
                                     head ? -1 : BODY.length);
        if (!head) {
          exchange.getResponseBody().write(BODY);
        }
      }
    } finally {
      exchange.close();
    }
  }

  @Override
  public String description() {
    return "answers 500 instead of dropping the connection when a handler throws";
  }
}
