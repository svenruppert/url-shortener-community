package junit.com.svenruppert.urlshortener.core;

import com.svenruppert.urlshortener.core.RedirectCycleDetector;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedirectCycleDetectorTest {

  private static final String BASE = "https://3g3.eu/";

  private static Function<String, Optional<String>> resolver(Map<String, String> codeToTarget) {
    return code -> Optional.ofNullable(codeToTarget.get(code));
  }

  @Test
  void externalTarget_noCycle() {
    var r = resolver(Map.of());
    assertFalse(RedirectCycleDetector.formsCycle(BASE, r, "a", "https://example.com"));
  }

  @Test
  void selfReference_isCycle() {
    var r = resolver(Map.of());
    // new code "a" points directly at itself
    assertTrue(RedirectCycleDetector.formsCycle(BASE, r, "a", BASE + "a"));
  }

  @Test
  void twoStepLoop_ABA_isCycle() {
    // existing: b -> base/a ; new: a -> base/b  => a→b→a
    var r = resolver(Map.of("b", BASE + "a"));
    assertTrue(RedirectCycleDetector.formsCycle(BASE, r, "a", BASE + "b"));
  }

  @Test
  void chainToExternal_noCycle() {
    // a -> base/b -> external
    var r = resolver(Map.of("b", "https://example.com"));
    assertFalse(RedirectCycleDetector.formsCycle(BASE, r, "a", BASE + "b"));
  }

  @Test
  void danglingReference_noCycle() {
    // a -> base/missing (no such mapping)
    var r = resolver(Map.of());
    assertFalse(RedirectCycleDetector.formsCycle(BASE, r, "a", BASE + "missing"));
  }

  @Test
  void extractCode_variants() {
    assertEquals("abc", RedirectCycleDetector.extractCode(BASE, BASE + "abc"));
    assertEquals("abc", RedirectCycleDetector.extractCode(BASE, BASE + "abc?x=1"));
    assertEquals(null, RedirectCycleDetector.extractCode(BASE, "https://example.com/abc"));
    assertEquals(null, RedirectCycleDetector.extractCode(BASE, BASE + "a/b"));
  }
}
