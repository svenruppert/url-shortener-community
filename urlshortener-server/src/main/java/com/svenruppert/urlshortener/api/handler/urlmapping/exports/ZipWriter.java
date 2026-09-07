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

    // From here on the status line is gone: 200 and chunked encoding are on the
    // wire before the first byte of content, and HTTP offers no way to take that
    // back. The previous version tried a second sendResponseHeaders(500, …) in
    // the catch — that call cannot succeed and its failure was swallowed, so a
    // failed export reached the client as "200 OK" plus a truncated archive.
    ex.sendResponseHeaders(httpStatus.code(), 0);

    OutputStream os = ex.getResponseBody();
    ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8);
    boolean complete = false;
    try {
      ZipEntry entry = new ZipEntry(zipEntryName);
      entry.setTime(System.currentTimeMillis());
      zip.putNextEntry(entry);

      // Write content directly into the ZIP entry (streaming)
      writer.writeTo(zip);

      zip.closeEntry();
      // finish() writes the central directory — the part that makes the bytes a
      // readable archive. It happens ONLY here, on the success path.
      zip.finish();
      complete = true;

    } catch (Exception e) {
      // Deliberately leave the archive unfinished: without a central directory
      // no zip reader accepts the file, so a failed export fails loudly at the
      // consumer instead of being filed away as a valid backup. Closing the
      // ZipOutputStream would call finish() and hand out exactly the
      // valid-looking, incomplete archive this is meant to prevent.
      HasLogger.staticLogger().error(
          "export aborted after the response had started — leaving the archive "
          + "unfinished on purpose (entry={}, file={})", zipEntryName, downloadFileName, e);

    } finally {
      // Only the raw stream is closed; `zip` is never closed, precisely so that
      // finish() cannot run on the failure path.
      closeQuietly(os, "response stream");
      closeQuietly(ex::close, "exchange");
      HasLogger.staticLogger().debug("writeZipStream done, complete={}", complete);
    }
  }

  private static void closeQuietly(AutoCloseable closeable, String what) {
    try {
      closeable.close();
    } catch (Exception e) {
      HasLogger.staticLogger().debug("closing {} failed: {}", what, e.toString());
    }
  }

  @FunctionalInterface
  public interface ZipEntryWriter {
    void writeTo(OutputStream out)
        throws IOException;
  }


}
