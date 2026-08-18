package com.svenruppert.urlshortener.api.handler.statistics.imports;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import tools.jackson.core.type.TypeReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.svenruppert.urlshortener.core.DefaultValues.STATISTICS_EXPORT_FORMAT_VERSION;
import static com.svenruppert.urlshortener.core.DefaultValues.STATISTICS_EXPORT_NDJSON_ENTRY;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class StatisticsImportReader
    implements HasLogger {

  private static final TypeReference<LinkedHashMap<String, Object>> MAP_REF =
      new TypeReference<>() { };

  private final StatisticsStore store;
  private final long maxNdjsonBytes;

  public StatisticsImportReader(StatisticsStore store, long maxNdjsonBytes) {
    this.store = store;
    this.maxNdjsonBytes = maxNdjsonBytes;
  }

  public Result importFrom(InputStream zipStream)
      throws IOException {
    ZipInputStream zis = new ZipInputStream(zipStream);
    ZipEntry entry;
    while ((entry = zis.getNextEntry()) != null) {
      String name = entry.getName();
      // Zip Slip Guard
      if (name.contains("..") || name.contains("\\") || name.startsWith("/")) {
        throw new IOException("Invalid zip entry name: " + name);
      }
      if (STATISTICS_EXPORT_NDJSON_ENTRY.equals(name)) {
        return readNdjson(new BoundedInputStream(zis, maxNdjsonBytes));
      }
    }
    throw new IOException("Expected entry not found: " + STATISTICS_EXPORT_NDJSON_ENTRY);
  }

  private Result readNdjson(InputStream in)
      throws IOException {
    long skippedLines = 0;
    LocalDate minDate = null;
    LocalDate maxDate = null;
    boolean headerSeen = false;
    List<RedirectEvent> events = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) continue;

        Map<String, Object> obj;
        try {
          obj = JacksonJson.mapper().readValue(line, MAP_REF);
        } catch (Exception e) {
          skippedLines++;
          logger().warn("Skipping unparseable line: {}", e.getMessage());
          continue;
        }

        if (!headerSeen) {
          headerSeen = true;
          if ("header".equals(obj.get("type"))) {
            String fmt = String.valueOf(obj.get("formatVersion"));
            if (!STATISTICS_EXPORT_FORMAT_VERSION.equals(fmt)) {
              throw new IOException("Unsupported formatVersion: " + fmt);
            }
            continue;
          }
          // No header — treat first line as event
        }

        RedirectEvent event = toEvent(obj);
        if (event == null) {
          skippedLines++;
          continue;
        }
        events.add(event);

        LocalDate date = event.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
        if (minDate == null || date.isBefore(minDate)) minDate = date;
        if (maxDate == null || date.isAfter(maxDate)) maxDate = date;
      }
    }
    // Idempotent import: events already present are skipped, so re-importing the
    // same data does not duplicate it (fixes double-counted aggregates).
    long added = store.importEventsDeduplicated(events);
    long duplicatesSkipped = events.size() - added;
    return new Result(added, skippedLines, minDate, maxDate, duplicatesSkipped);
  }

  private RedirectEvent toEvent(Map<String, Object> obj) {
    Object shortCodeRaw = obj.get("shortCode");
    Object timestampRaw = obj.get("timestamp");
    if (shortCodeRaw == null || timestampRaw == null) return null;
    try {
      Instant ts = Instant.parse(String.valueOf(timestampRaw));
      return new RedirectEvent(
          String.valueOf(shortCodeRaw),
          ts,
          asString(obj.get("userAgent")),
          asString(obj.get("referer")),
          asString(obj.get("ipHash")),
          asString(obj.get("acceptLanguage"))
      );
    } catch (Exception e) {
      return null;
    }
  }

  private static String asString(Object o) {
    return (o == null) ? null : String.valueOf(o);
  }

  public record Result(long importedEvents, long skippedLines, LocalDate minDate, LocalDate maxDate,
                       long duplicatesSkipped) { }

  /**
   * Caps bytes flowing through a stream — uncompressed zip-entry content
   * (zip-bomb guard) and, since SEC-M3 (V00.17.00 P070), also the compressed
   * upload itself, which was previously unbounded server-side.
   */
  public static final class BoundedInputStream
      extends InputStream {
    private final InputStream delegate;
    private final long maxBytes;
    private final String label;
    private long count = 0;

    BoundedInputStream(InputStream delegate, long maxBytes) {
      this(delegate, maxBytes, "Uncompressed content");
    }

    public BoundedInputStream(InputStream delegate, long maxBytes, String label) {
      this.delegate = delegate;
      this.maxBytes = maxBytes;
      this.label = label;
    }

    @Override
    public int read()
        throws IOException {
      int r = delegate.read();
      if (r != -1) {
        count++;
        checkLimit();
      }
      return r;
    }

    @Override
    public int read(byte[] b, int off, int len)
        throws IOException {
      int r = delegate.read(b, off, len);
      if (r > 0) {
        count += r;
        checkLimit();
      }
      return r;
    }

    private void checkLimit()
        throws SizeLimitExceededException {
      if (count > maxBytes) {
        throw new SizeLimitExceededException(label + " exceeds limit=" + maxBytes);
      }
    }
  }

  /** Marker so the handler can answer 413 instead of a generic 400. */
  public static final class SizeLimitExceededException extends IOException {
    SizeLimitExceededException(String message) {
      super(message);
    }
  }
}
