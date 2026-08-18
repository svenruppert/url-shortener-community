package junit.com.svenruppert.urlshortener.api;

import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilsTest {

  @Test
  void parseValidJson() throws IOException {
    String json = "{\"key\":\"value\",\"number\":\"42\"}";
    ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    Map<String, String> result = JsonUtils.parseJson(input);
    assertEquals(2, result.size());
    assertEquals("value", result.get("key"));
    assertEquals("42", result.get("number"));
  }

  @Test
  void parseEmptyJsonReturnsEmptyMap() throws IOException {
    String json = "{}";
    ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    Map<String, String> result = JsonUtils.parseJson(input);
    assertTrue(result.isEmpty());
  }

  @Test
  void parseInvalidJsonThrowsException() {
    String json = "\"not a json object\"";
    ByteArrayInputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    assertThrows(MismatchedInputException.class, () -> JsonUtils.parseJson(input));
  }

  @Test
  void toJsonProducesValidOutput() {
    Map<String, String> map = Map.of("foo", "bar", "num", "123");
    String json = JsonUtils.toJson(map);
    assertTrue(json.contains("\"foo\":\"bar\""));
    assertTrue(json.contains("\"num\":\"123\""));
    assertTrue(json.startsWith("{") && json.endsWith("}"));
  }

  @Test
  void toJsonHandlesEscaping() {
    Map<String, String> map = Map.of("a", "b\"", "c\"", "d");
    String json = JsonUtils.toJson(map);
    assertTrue(json.contains("\\\""));
  }

  @Test
  void shortUrlMapping_toJson() {
    ShortUrlMapping m = new ShortUrlMapping(
        "abc123",
        "https://example.com/page",
        Instant.parse("2025-10-13T10:15:30Z"),
        null,
        true
    );

    String json = JsonUtils.toJson(m);

    assertTrue(json.contains("\"shortCode\":\"abc123\""));
    assertTrue(json.contains("\"originalUrl\":\"https://example.com/page\""));
    assertTrue(json.contains("\"createdAt\":\"2025-10-13T10:15:30Z\""));
  }

}