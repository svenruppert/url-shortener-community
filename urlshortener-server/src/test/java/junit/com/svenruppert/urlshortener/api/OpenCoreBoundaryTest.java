package junit.com.svenruppert.urlshortener.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V00.18.00 P030 — open-core boundary (concept §9): community sources must
 * never reference an Enterprise identifier. The compiler catches direct
 * imports; this test also catches string-based lookups and reflection by
 * scanning the raw sources. The build fails on the first violation.
 */
class OpenCoreBoundaryTest {

  private static final String FORBIDDEN = "urlshortener.enterprise";

  @Test
  void communitySourcesReferenceNoEnterpriseIdentifiers()
      throws IOException {
    Path mainSources = Path.of(System.getProperty("user.dir"), "src", "main", "java");
    assertTrue(Files.isDirectory(mainSources), "source tree not found: " + mainSources);
    try (Stream<Path> files = Files.walk(mainSources)) {
      List<Path> violations = files
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> {
            try {
              return Files.readString(p).contains(FORBIDDEN);
            } catch (IOException unreadable) {
              throw new IllegalStateException("cannot read " + p, unreadable);
            }
          })
          .toList();
      assertTrue(violations.isEmpty(),
          "open-core boundary violated — community sources reference '" + FORBIDDEN + "': " + violations);
    }
  }
}
