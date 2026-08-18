package junit.com.svenruppert.urlshortener.api.handler.statistics;

import com.svenruppert.urlshortener.api.handler.statistics.imports.StatisticsImportReader;
import com.svenruppert.urlshortener.api.utils.JsonWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V00.17.00 P070 — SEC-M3 (server-side compressed bound for the statistics
 * import) and SEC-L4 (full JSON string escaping for hand-built JSON).
 */
class StatisticsImportBoundsTest {

  @Test
  void boundedStreamStopsAtTheLimitWithItsLabel() {
    var oversized = new ByteArrayInputStream(new byte[64]);
    var bounded = new StatisticsImportReader.BoundedInputStream(oversized, 16, "Compressed upload");

    var ex = assertThrows(StatisticsImportReader.SizeLimitExceededException.class,
        () -> bounded.readAllBytes(),
        "SEC-M3: reading past the limit must abort");
    assertTrue(ex.getMessage().startsWith("Compressed upload"),
        "the limit violation must name what was bounded: " + ex.getMessage());
  }

  @Test
  void boundedStreamPassesContentWithinTheLimit()
      throws Exception {
    var ok = new ByteArrayInputStream(new byte[16]);
    var bounded = new StatisticsImportReader.BoundedInputStream(ok, 16, "Compressed upload");
    assertEquals(16, bounded.readAllBytes().length);
  }

  @Test
  void jsonEscapingCoversControlCharacters() {
    assertEquals("a\\\\b\\\"c", JsonWriter.escapeJsonString("a\\b\"c"),
        "backslash and quote keep their escapes");
    assertEquals("line\\nbreak\\ttab\\r", JsonWriter.escapeJsonString("line\nbreak\ttab\r"),
        "named control escapes");
    assertEquals("nul\\u0000bell\\u0007", JsonWriter.escapeJsonString("nul" + '\u0000' + "bell" + '\u0007'),
        "SEC-L4: remaining control characters become \\u-escapes");
    assertEquals("", JsonWriter.escapeJsonString(null));
  }
}
