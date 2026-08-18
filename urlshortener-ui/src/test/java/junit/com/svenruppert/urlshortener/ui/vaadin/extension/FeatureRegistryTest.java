package junit.com.svenruppert.urlshortener.ui.vaadin.extension;

import com.svenruppert.urlshortener.ui.vaadin.extension.FeatureRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V00.18.00 P030 — the UI seam discovers plug-ins via ServiceLoader (the
 * test-scope {@link TestFeature}) and aggregates their contributions. In the
 * production artifact no plug-in is present and every accessor is empty —
 * guaranteed by the boundary test plus the plug-in living in test scope only.
 */
class FeatureRegistryTest {

  @Test
  void discoversTheTestFeatureAndAggregatesContributions() {
    assertTrue(FeatureRegistry.all().stream()
            .anyMatch(c -> "test-feature".equals(c.featureId())),
        "ServiceLoader discovery must find the test-scope plug-in");

    var route = FeatureRegistry.routes().stream()
        .filter(r -> "test-feature".equals(r.path())).findFirst().orElseThrow();
    assertEquals(com.vaadin.flow.component.html.Span.class, route.view());

    var menu = FeatureRegistry.menuItems().stream()
        .filter(m -> "test-feature".equals(m.path())).findFirst().orElseThrow();
    assertEquals("Test Feature", menu.fallback());
    assertTrue(menu.adminOnly());

    assertEquals(1, FeatureRegistry.navbarComponents().size());
  }
}
