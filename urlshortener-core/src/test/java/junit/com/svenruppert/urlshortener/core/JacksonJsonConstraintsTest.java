package junit.com.svenruppert.urlshortener.core;

import com.svenruppert.urlshortener.core.JacksonJson;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SEC-L3 regression lock (V00.17.00 P070): the stream-read constraints must
 * actually be applied. The previous reflective setup looked for the Jackson 2
 * class names on a Jackson 3 classpath, silently failed and left every parser
 * unconstrained — this test was red against that state.
 */
class JacksonJsonConstraintsTest {

  @Test
  void oversizedNumberLiteralIsRejected() {
    String json = "{\"n\":" + "9".repeat(1_001) + "}";
    assertThrows(Exception.class,
        () -> JacksonJson.mapper().readValue(json, Map.class),
        "maxNumberLength(1000) must reject a 1001-digit literal");
  }

  @Test
  void normalPayloadStillParses() {
    Map<?, ?> parsed = JacksonJson.mapper().readValue("{\"n\":42,\"s\":\"ok\"}", Map.class);
    assertEquals(42, parsed.get("n"));
    assertEquals("ok", parsed.get("s"));
  }
}
