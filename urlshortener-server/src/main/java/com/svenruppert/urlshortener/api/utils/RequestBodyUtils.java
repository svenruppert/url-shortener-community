package com.svenruppert.urlshortener.api.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;

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
          throw new IOException("Request body too large (" + total + " bytes). Limit is " +
                                    MAX_BODY_SIZE_BYTES + " bytes.");
        }
        sb.append(buffer, 0, read);
      }

      return sb.toString();
    }
  }

  public static byte[] readBodyBytes(InputStream in, int maxBytes) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int read;
    int total = 0;

    while ((read = in.read(buf)) != -1) {
      total += read;
      if (total > maxBytes) {
        throw new IOException("Request body too large (" + total + " bytes). Limit is " + maxBytes);
      }
      bos.write(buf, 0, read);
    }
    return bos.toByteArray();
  }

}
