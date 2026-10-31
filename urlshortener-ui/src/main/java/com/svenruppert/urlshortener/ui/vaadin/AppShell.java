package com.svenruppert.urlshortener.ui.vaadin;


import com.svenruppert.dependencies.core.logger.HasLogger;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.aura.Aura;
import eu.jsentinel.design.vaadin.JSentinelTheme;

/**
 * Application shell. The page around every view uses Vaadin's Aura theme with the
 * jSentinel brand layered on top (GUI makeover 00.21.00), the same foundation jMemoria
 * uses. Light and dark follow the operating system unless the person picks one in the
 * navbar; a stored choice is applied before the first paint. The {@code @PWA} annotation
 * keeps the application installable on phones, tablets and some desktop browsers.
 */
@PWA(name = "An OpenSource URL Shortener", shortName = "URL Shortener")
@StyleSheet(Aura.STYLESHEET)
@StyleSheet(JSentinelTheme.STYLESHEET)
@Push
public class AppShell
    implements AppShellConfigurator, HasLogger {

  @Override
  public void configurePage(AppShellSettings settings) {
    AppShellConfigurator.super.configurePage(settings);
    // Apply a stored light/dark choice before the first paint, on every page.
    settings.addInlineWithContents(JSentinelTheme.COLOR_SCHEME_BOOTSTRAP,
        Inline.Wrapping.JAVASCRIPT);
    logger().info("configurePage .. Aura + jSentinel brand applied.");
  }
}
