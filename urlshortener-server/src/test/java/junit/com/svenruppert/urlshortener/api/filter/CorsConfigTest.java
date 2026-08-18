package junit.com.svenruppert.urlshortener.api.filter;

import com.svenruppert.urlshortener.api.filter.CorsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

  @AfterEach
  void clear() {
    System.clearProperty(CorsConfig.PROPERTY);
  }

  @Test
  void emptyByDefault_blocksEverything() {
    System.clearProperty(CorsConfig.PROPERTY);
    assertTrue(CorsConfig.allowedOrigins().isEmpty());
    assertFalse(CorsConfig.isAllowed("https://anything.example"));
  }

  @Test
  void parsesCommaSeparatedAllowlist() {
    System.setProperty(CorsConfig.PROPERTY, "https://x.example , https://y.example");
    assertTrue(CorsConfig.isAllowed("https://x.example"));
    assertTrue(CorsConfig.isAllowed("https://y.example"));
    assertFalse(CorsConfig.isAllowed("https://z.example"));
    assertFalse(CorsConfig.isAllowed(null));
  }
}
