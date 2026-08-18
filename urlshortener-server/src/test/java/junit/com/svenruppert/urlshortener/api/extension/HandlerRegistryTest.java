package junit.com.svenruppert.urlshortener.api.extension;

import com.svenruppert.urlshortener.api.extension.HandlerContribution;
import com.svenruppert.urlshortener.api.extension.HandlerRegistry;
import com.svenruppert.urlshortener.api.extension.ServerContext;
import com.sun.net.httpserver.HttpHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V00.18.00 P030 — fail-fast validation of the server plug-in seam: duplicate
 * or malformed contribution paths must abort startup instead of silently
 * shadowing endpoints.
 */
class HandlerRegistryTest {

  private record Probe(String p) implements HandlerContribution {
    @Override
    public String path() {
      return p;
    }

    @Override
    public HttpHandler create(ServerContext context) {
      return exchange -> exchange.close();
    }
  }

  @Test
  void duplicatePathsAbort() {
    var ex = assertThrows(IllegalStateException.class, () ->
        HandlerRegistry.validate(List.of(new Probe("/api/x"), new Probe("/api/x"))));
    assertTrue(ex.getMessage().contains("/api/x"), ex.getMessage());
  }

  @Test
  void invalidPathsAbort() {
    assertThrows(IllegalStateException.class, () ->
        HandlerRegistry.validate(List.of(new Probe("no-leading-slash"))));
    assertThrows(IllegalStateException.class, () ->
        HandlerRegistry.validate(List.of(new Probe(" "))));
  }

  @Test
  void distinctPathsPass() {
    assertDoesNotThrow(() ->
        HandlerRegistry.validate(List.of(new Probe("/api/x"), new Probe("/api/y"))));
  }
}
