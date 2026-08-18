package junit.com.svenruppert.urlshortener.ui.vaadin.extension;

import com.svenruppert.urlshortener.ui.vaadin.extension.FeatureContribution;
import com.svenruppert.urlshortener.ui.vaadin.extension.MenuContribution;
import com.svenruppert.urlshortener.ui.vaadin.extension.NavbarContribution;
import com.svenruppert.urlshortener.ui.vaadin.extension.RouteContribution;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

import java.util.List;

/**
 * Test plug-in registered via META-INF/services (test scope) — proves the UI
 * seam discovers contributions without any change to community sources.
 */
public class TestFeature
    implements FeatureContribution {

  @Override
  public String featureId() {
    return "test-feature";
  }

  @Override
  public List<RouteContribution> routes() {
    return List.of(new RouteContribution("test-feature", Span.class));
  }

  @Override
  public List<MenuContribution> menuItems() {
    return List.of(new MenuContribution("test.feature", "Test Feature", "test-feature",
        VaadinIcon.PUZZLE_PIECE, true));
  }

  @Override
  public List<NavbarContribution> navbarComponents() {
    return List.of(() -> new Span("test-navbar"));
  }
}
