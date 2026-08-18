package com.svenruppert.urlshortener.api.utils;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.logger.HasLogger;

import java.io.IOException;
import java.util.Objects;

import static com.svenruppert.dependencies.core.net.HttpStatus.METHOD_NOT_ALLOWED;
import static com.svenruppert.dependencies.core.net.HttpStatus.NO_CONTENT;


/**
 * Centralised utility for validating and enforcing supported HTTP methods within HttpHandlers.
 * Provides consistent handling for OPTIONS requests and method rejection responses.
 */
public final class RequestMethodUtils
    implements HasLogger {

  private RequestMethodUtils() {
    // utility class, no instances
  }

  // ======== GET ========

  /**
   * Allows only GET requests.
   * Any other method is rejected with HTTP 405 (Method Not Allowed).
   */
  public static boolean requireGet(HttpExchange exchange)
      throws IOException {
    HasLogger.staticLogger().debug("handle ... {} ", exchange.getRequestMethod());
    Objects.requireNonNull(exchange, "exchange");
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      ErrorResponses.withStatus(exchange, METHOD_NOT_ALLOWED,
                                "Only GET is allowed for this endpoint.");

      return false;
    }
    return true;
  }

  // ======== POST ========

  /**
   * Allows POST requests and responds to OPTIONS with 204 (No Content).
   * All other methods trigger a 405 (Method Not Allowed) response.
   */
  public static boolean requirePost(HttpExchange exchange)
      throws IOException {
    return requireWithOptions(exchange, "POST");
  }

  // ======== PUT ========

  /**
   * Allows PUT requests and responds to OPTIONS with 204 (No Content).
   * All other methods trigger a 405 (Method Not Allowed) response.
   */
  public static boolean requirePut(HttpExchange exchange)
      throws IOException {
    return requireWithOptions(exchange, "PUT");
  }

  // ======== DELETE ========

  /**
   * Allows DELETE requests and responds to OPTIONS with 204 (No Content).
   * All other methods trigger a 405 (Method Not Allowed) response.
   */
  public static boolean requireDelete(HttpExchange exchange)
      throws IOException {
    return requireWithOptions(exchange, "DELETE");
  }

  // ======== HEAD / PATCH (optional) ========

  public static boolean requireHead(HttpExchange exchange)
      throws IOException {
    return requireWithOptions(exchange, "HEAD");
  }

  public static boolean requirePatch(HttpExchange exchange)
      throws IOException {
    return requireWithOptions(exchange, "PATCH");
  }


  /**
   * Allows GET or HEAD requests.
   * Any other method is rejected with HTTP 405 (Method Not Allowed).
   */
  public static boolean requireGetOrHead(HttpExchange exchange)
      throws IOException {
    HasLogger.staticLogger().debug("handle ... {} ", exchange.getRequestMethod());
    Objects.requireNonNull(exchange, "exchange");

    String method = exchange.getRequestMethod();
    if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
      // keep behaviour consistent with requireGet: return a JSON error
      ErrorResponses.withStatus(exchange, METHOD_NOT_ALLOWED,
                                "Only GET or HEAD is allowed for this endpoint.");

      return false;
    }
    return true;
  }

  // ======== internal helper ========

  private static boolean requireWithOptions(HttpExchange exchange,
                                            String allowedPrimary)
      throws IOException {
    HasLogger.staticLogger().debug("handle requireWithOptions ... {} ", exchange.getRequestMethod());

    Objects.requireNonNull(exchange, "exchange");
    String method = exchange.getRequestMethod();

    if ("OPTIONS".equalsIgnoreCase(method)) {
      exchange.getResponseHeaders().add("Allow", allowedPrimary + ", OPTIONS");
      exchange.sendResponseHeaders(NO_CONTENT.code(), -1);
      return false;
    }

    if (!allowedPrimary.equalsIgnoreCase(method)) {
      exchange.getResponseHeaders().add("Allow", allowedPrimary + ", OPTIONS");
      ErrorResponses.withStatus(
          exchange,
          METHOD_NOT_ALLOWED,
          "Only " + allowedPrimary + " or OPTIONS are permitted for this endpoint."
      );

      return false;
    }

    return true;
  }

  public static void allow(HttpExchange ex, String allow)
      throws IOException {
    ex.getResponseHeaders().add("Allow", allow);
    ex.sendResponseHeaders(NO_CONTENT.code(), -1);
  }

  public static void methodNotAllowed(HttpExchange ex, String allow)
      throws IOException {
    ex.getResponseHeaders().add("Allow", allow);
    ErrorResponses.withStatus(ex, METHOD_NOT_ALLOWED, "Method not allowed");
  }


}
