package com.svenruppert.urlshortener.api.utils;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.JsonUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility methods for safely reading HTTP request bodies.
 */
public final class RequestBodyUtils {

  /** Maximum number of bytes to read before aborting (protection against abuse). */
  private static final int MAX_BODY_SIZE_BYTES = 1_000_000; // 1 MB safety limit

  private RequestBodyUtils() {
    // utility class
  }

  /**
   * Reads the entire body of the request as a UTF-8 String.
   * <p>
   * The method ensures:
   * <ul>
   *   <li>All bytes are read safely with buffering</li>
   *   <li>UTF-8 decoding is enforced (independent of client header)</li>
   *   <li>Input streams are closed automatically</li>
   *   <li>Overly large bodies trigger an IOException</li>
   * </ul>
   *
   * @param in the InputStream obtained from {@code exchange.getRequestBody()}
   * @return the request body as a String
   * @throws IOException if reading fails or exceeds {@link #MAX_BODY_SIZE_BYTES}
   */
  public static String readBody(InputStream in) throws IOException {
    if (in == null) {
      return "";
    }

    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(in, StandardCharsets.UTF_8))) {

      StringBuilder sb = new StringBuilder();
      char[] buffer = new char[4096];
      int read;
      int total = 0;

      while ((read = reader.read(buffer)) != -1) {
        total += read;
        if (total > MAX_BODY_SIZE_BYTES) {
          throw new BodyTooLargeException("Request body too large (" + total + " bytes). Limit is " +
                                    MAX_BODY_SIZE_BYTES + " bytes.");
        }
        sb.append(buffer, 0, read);
      }

      return sb.toString();
    }
  }

  /**
   * Reads the body as a flat JSON object, or answers 400 and returns {@code null}.
   *
   * <p>Jackson 3 reports parse failures as unchecked {@code JacksonException}
   * ({@code extends RuntimeException}). A handler that catches only
   * {@code IOException} therefore lets a malformed body escape {@code handle()},
   * and the JDK {@code HttpServer} closes the connection without a status line —
   * the client gets no answer at all. A body the caller sent is the caller's
   * responsibility, so the correct answer is 400, not 500 and not silence.
   *
   * <p>Follows the {@code RequestMethodUtils.requireGet} convention: a
   * {@code null} return means the response has already been written, so the
   * caller returns immediately. A blank body yields an empty map, letting the
   * handler's own required-field checks produce the more specific message.
   *
   * @return the parsed body, or {@code null} when 400 has already been sent
   */
  public static Map<String, String> jsonMapOrBadRequest(HttpExchange exchange)
      throws IOException {
    try {
      Map<String, String> body = JsonUtils.parseJson(readBody(exchange.getRequestBody()));
      return body == null ? Map.of() : body;
    } catch (IOException | RuntimeException malformed) {
      HasLogger.staticLogger().debug("malformed JSON body: {}", malformed.toString());
      ErrorResponses.badRequest(exchange, "invalid_json", "Request body is not valid JSON");
      return null;
    }
  }

  /**
   * Reads the body into {@code type}, or answers 400 and returns {@code null}.
   *
   * <p>Same contract as {@link #jsonMapOrBadRequest(HttpExchange)}. A body that
   * parses to {@code null} is treated as malformed — a typed endpoint needs an
   * object to work with.
   *
   * @return the parsed body, or {@code null} when 400 has already been sent
   */
  public static <T> T jsonBodyOrBadRequest(HttpExchange exchange, Class<T> type)
      throws IOException {
    return parseOrBadRequest(exchange, readBody(exchange.getRequestBody()), type);
  }

  /**
   * Parses an already-read body into {@code type}, or answers 400 and returns
   * {@code null}.
   *
   * <p>For the handlers that need the raw text as well (logging, size checks)
   * and therefore cannot let the helper read the stream a second time — a body
   * can only be consumed once.
   *
   * @return the parsed body, or {@code null} when 400 has already been sent
   */
  public static <T> T parseOrBadRequest(HttpExchange exchange, String rawBody, Class<T> type)
      throws IOException {
    try {
      // JsonUtils.fromJson, not the raw mapper: it adds the per-type field
      // validation (e.g. a ShortenRequest without a url) and reports it as an
      // IllegalArgumentException whose message names the offending field.
      T body = JsonUtils.fromJson(rawBody, type);
      if (body == null) {
        ErrorResponses.badRequest(exchange, "invalid_json", "Request body is not valid JSON");
      }
      return body;
    } catch (RuntimeException malformed) {
      HasLogger.staticLogger().debug("malformed JSON body: {}", malformed.toString());
      ErrorResponses.badRequest(exchange, "invalid_json",
                                messageOf(malformed, "Request body is not valid JSON"));
      return null;
    }
  }

  /**
   * Keeps a validation message that names the offending field, but never leaks a
   * parser's internal text (position, class names) to the caller.
   */
  private static String messageOf(Exception e, String fallback) {
    return e instanceof IllegalArgumentException && e.getMessage() != null
           ? e.getMessage()
           : fallback;
  }

  public static byte[] readBodyBytes(InputStream in, int maxBytes) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int read;
    int total = 0;

    while ((read = in.read(buf)) != -1) {
      total += read;
      if (total > maxBytes) {
        throw new BodyTooLargeException(
            "Request body too large (" + total + " bytes). Limit is " + maxBytes);
      }
      bos.write(buf, 0, read);
    }
    return bos.toByteArray();
  }


  /**
   * The caller sent more than the endpoint accepts.
   *
   * <p>Its own type, because "too large" is a 413 and every other read failure
   * is not. Both size limits used to throw a plain IOException, so the two
   * import endpoints answered the same situation differently — 413 in one,
   * 400 in the other.
   */
  public static final class BodyTooLargeException
      extends IOException {

    public BodyTooLargeException(String message) {
      super(message);
    }
  }
}
