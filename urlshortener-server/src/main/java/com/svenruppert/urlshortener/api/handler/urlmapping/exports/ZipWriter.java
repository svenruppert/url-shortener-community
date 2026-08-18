package com.svenruppert.urlshortener.api.handler.urlmapping.exports;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.svenruppert.dependencies.core.net.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.svenruppert.urlshortener.core.DefaultValues.*;

public class ZipWriter
    implements HasLogger {

  private ZipWriter() {
  }

  /**
   *
   * @param ex
   * @param httpStatus
   * @param zipEntryName
   * @param downloadFileName
   * @param writer
   * @throws IOException
   */
  public static void writeZipStream(HttpExchange ex,
                                    HttpStatus httpStatus,
                                    String zipEntryName,
                                    String downloadFileName,
                                    ZipEntryWriter writer)
      throws IOException {

    Objects.requireNonNull(ex, "ex");
    Objects.requireNonNull(httpStatus, "httpStatus");
    Objects.requireNonNull(zipEntryName, "zipEntryName");
    Objects.requireNonNull(downloadFileName, "downloadFileName");
    Objects.requireNonNull(writer, "writer");

    HasLogger.staticLogger().info("writeZipStream {}, entry={}", httpStatus, zipEntryName);

    Headers h = ex.getResponseHeaders();
    h.add(CONTENT_TYPE, MediaType.APPLICATION_ZIP.mime());
    h.add(CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"");
    h.add("X-Content-Type-Options", "nosniff");

    ex.sendResponseHeaders(httpStatus.code(), 0);

    try (OutputStream os = ex.getResponseBody();
         ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8)) {

      ZipEntry entry = new ZipEntry(zipEntryName);
      entry.setTime(System.currentTimeMillis());
      zip.putNextEntry(entry);

      // Write content directly into the ZIP entry (streaming)
      writer.writeTo(zip);

      zip.closeEntry();
      zip.finish();

    } catch (Exception e) {
      HasLogger.staticLogger().info("writeZipStream (catch) {}", e.getMessage());

      try {
        byte[] body = ("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}")
            .getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set(CONTENT_TYPE, MediaType.APPLICATION_JSON.withCharsetUtf8());
        ex.sendResponseHeaders(INTERNAL_SERVER_ERROR.code(), body.length);
        ex.getResponseBody().write(body);
      } catch (Exception ignoredI) {
        HasLogger.staticLogger().info("writeZipStream (catch - ignored I) {} ", ignoredI.getMessage());
      }

    } finally {
      try {
        ex.close();
      } catch (Exception ignoredII) {
        HasLogger.staticLogger().info("writeZipStream (finally - ignored II) {} ", ignoredII.getMessage());
      }
    }
  }

  /**
   * Minimal JSON string escaping to avoid breaking the fallback error payload.
   */
  private static String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  @FunctionalInterface
  public interface ZipEntryWriter {
    void writeTo(OutputStream out)
        throws IOException;
  }


}
