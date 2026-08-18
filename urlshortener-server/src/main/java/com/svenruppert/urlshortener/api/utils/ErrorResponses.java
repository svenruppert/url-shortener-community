package com.svenruppert.urlshortener.api.utils;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.urlshortener.core.JsonUtils;

import java.io.IOException;

import static com.svenruppert.dependencies.core.net.HttpStatus.*;
import static com.svenruppert.urlshortener.api.utils.JsonWriter.writeJson;

/**
 * Centralised factory for consistent JSON error responses.
 * <p>
 * Purpose:
 * - eliminate duplicated inline error JSON
 * - guarantee stable response format
 * - avoid accidental JSON injection
 * <p>
 * Error format (stable contract):
 * {
 * "error": "<message>",
 * "code": "<optional machine readable code>"
 * }
 */
public final class ErrorResponses {

  private ErrorResponses() {
  }

  // ---------------------------------------------------------------------------
  // Core helpers
  // ---------------------------------------------------------------------------

  private static String errorJson(String message) {
    return "{\"error\":\"" + JsonUtils.escape(message) + "\"}";
  }

  private static String errorJson(String code, String message) {
    return "{\"error\":\"" + JsonUtils.escape(message) +
        "\",\"code\":\"" + JsonUtils.escape(code) + "\"}";
  }

  private static void write(HttpExchange ex, HttpStatus status, String json)
      throws IOException {
    writeJson(ex, status, json);
  }

  // ---------------------------------------------------------------------------
  // 400 – Bad Request
  // ---------------------------------------------------------------------------

  public static void badRequest(HttpExchange ex, String message)
      throws IOException {
    write(ex, BAD_REQUEST, errorJson(message));
  }

  public static void badRequest(HttpExchange ex, String code, String message)
      throws IOException {
    write(ex, BAD_REQUEST, errorJson(code, message));
  }

  // Common bad-request cases
  public static void missingParameter(HttpExchange ex, String param)
      throws IOException {
    badRequest(ex, "missing_parameter", "missing parameter: " + param);
  }

  public static void invalidParameter(HttpExchange ex, String param)
      throws IOException {
    badRequest(ex, "invalid_parameter", "invalid parameter: " + param);
  }

  // ---------------------------------------------------------------------------
  // 404 – Not Found
  // ---------------------------------------------------------------------------

  public static void notFound(HttpExchange ex, String message)
      throws IOException {
    write(ex, NOT_FOUND, errorJson(message));
  }

  public static void notFound(HttpExchange ex, String code, String message)
      throws IOException {
    write(ex, NOT_FOUND, errorJson(code, message));
  }

  // Common not-found cases
  public static void stagingNotFound(HttpExchange ex)
      throws IOException {
    notFound(ex, "staging_not_found", "stagingId not found");
  }

  // ---------------------------------------------------------------------------
  // 401 – Unauthorized / 403 – Forbidden
  // ---------------------------------------------------------------------------

  public static void unauthorized(HttpExchange ex)
      throws IOException {
    write(ex, UNAUTHORIZED, errorJson("unauthorized", "authentication required"));
  }

  public static void forbidden(HttpExchange ex, String permission)
      throws IOException {
    write(ex, FORBIDDEN,
          errorJson("forbidden", "missing permission: " + permission));
  }

  // ---------------------------------------------------------------------------
  // 503 – Service Unavailable (first-admin bootstrap pending)
  // ---------------------------------------------------------------------------

  public static void serviceUnavailable(HttpExchange ex, String message)
      throws IOException {
    write(ex, SERVICE_UNAVAILABLE, errorJson("bootstrap_required", message));
  }

  // ---------------------------------------------------------------------------
  // 405 – Method Not Allowed
  // ---------------------------------------------------------------------------

  public static void methodNotAllowed(HttpExchange ex, String allowHeader)
      throws IOException {
    ex.getResponseHeaders().set("Allow", allowHeader);
    write(ex, METHOD_NOT_ALLOWED,
          errorJson("method_not_allowed",
                    "HTTP method not allowed"));
  }

  // ---------------------------------------------------------------------------
  // 409 – Conflict
  // ---------------------------------------------------------------------------

  public static void conflict(HttpExchange ex, String message)
      throws IOException {
    write(ex, CONFLICT, errorJson(message));
  }

  public static void conflict(HttpExchange ex, String code, String message)
      throws IOException {
    write(ex, CONFLICT, errorJson(code, message));
  }

  // ---------------------------------------------------------------------------
  // 500 – Internal Server Error
  // ---------------------------------------------------------------------------

  public static void internalServerError(HttpExchange ex, String message)
      throws IOException {
    write(ex, INTERNAL_SERVER_ERROR, errorJson(message));
  }

  public static void internalServerError(HttpExchange ex, Exception e)
      throws IOException {
    write(ex, INTERNAL_SERVER_ERROR,
          errorJson("internal_error",
                    e.getMessage() != null ? e.getMessage() : "internal error"));
  }

  public static void withStatus(HttpExchange ex, int status, String message)
      throws IOException {
    writeJson(ex, HttpStatus.fromCode(status),
              "{\"error\":\"" + JsonUtils.escape(message) + "\"}");
  }

  public static void withStatus(HttpExchange ex, HttpStatus status, String message)
      throws IOException {
    writeJson(ex, status,
              "{\"error\":\"" + JsonUtils.escape(message) + "\"}");
  }

}
