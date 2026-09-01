package com.svenruppert.urlshortener.ui.vaadin;



import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.AliasPolicy;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.security.LoginConfig;
import eu.jsentinel.jcustos.authorization.api.SubjectStores;
import com.svenruppert.urlshortener.ui.vaadin.tools.LocaleSelection;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.*;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vaadin 25 ServiceInitListener
 * - Browser-Locale als Default
 * - Beschränkt auf DE / EN / FI
 * - Session-basierte Sprachwahl
 * - Registriert alle Vaadin-Routen als reservierte Aliase (AliasPolicy)
 */
public class AppServiceInitListener implements VaadinServiceInitListener, HasLogger {

  private static final AtomicBoolean ROUTES_RESERVED = new AtomicBoolean(false);

  @Override
  public void serviceInit(ServiceInitEvent event) {

    reserveAllRoutes();

    event.getSource().addUIInitListener(uiEvent -> {

      UI ui = uiEvent.getUI();
      VaadinSession session = ui.getSession();

      if (!LoginConfig.isLoginEnabled()
          && SubjectStores.subjectStore().currentSubject(AppUser.class).isEmpty()) {
        SubjectStores.subjectStore()
            .setCurrentSubject(new AppUser("guest", Set.of(AppRole.USER)), AppUser.class);
      }

      // 1) Falls Benutzer in Session bereits Sprache gewählt hat
      Locale sessionLocale = LocaleSelection.getFromSession(session);
      if (sessionLocale != null) {
        ui.setLocale(sessionLocale);
        session.setLocale(sessionLocale);
        return;
      }

      // 2) Sonst Browser-Locale verwenden (Accept-Language)
      Locale browserLocale = ui.getLocale();
      Locale effective = LocaleSelection.match(browserLocale);

      ui.setLocale(effective);
      session.setLocale(effective);
      LocaleSelection.setToSession(session, effective);
    });
  }

  /**
   * Registers every registered Vaadin route's top-level segment as a reserved
   * alias, so a short code can never shadow a real route (P030 — dynamic
   * RESERVED against all routes). Runs once per JVM; failures are non-fatal.
   */
  private void reserveAllRoutes() {
    if (!ROUTES_RESERVED.compareAndSet(false, true)) return;
    try {
      List<String> segments = RouteConfiguration.forApplicationScope()
          .getAvailableRoutes().stream()
          .map(route -> route.getTemplate())
          .filter(t -> t != null && !t.isBlank())
          .map(t -> t.split("/", 2)[0])
          .filter(s -> !s.isBlank() && !s.startsWith(":"))
          .distinct()
          .toList();
      AliasPolicy.reserve(segments);
      logger().info("reserved {} Vaadin route segments as aliases: {}", segments.size(), segments);
    } catch (Exception e) {
      logger().warn("could not enumerate Vaadin routes for AliasPolicy.reserve: {}", e.getMessage());
    }
  }
}