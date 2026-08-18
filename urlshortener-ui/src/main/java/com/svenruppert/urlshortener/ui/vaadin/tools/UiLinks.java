package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.Locale;

/**
 * Factory helpers for link components.
 * HTTP links are flagged with a visible warning icon so users immediately see
 * that the target URL is not encrypted.
 *
 * <p>Defense-in-depth (V00.17.00 P090): every {@code _blank} link carries
 * {@code rel="noopener noreferrer"} (SEC-L8, reverse tabnabbing) and hrefs
 * pass a scheme allowlist — {@code http(s)} and relative paths only — so a
 * stored {@code javascript:} URL never becomes a clickable anchor (SEC-L9).
 */
public final class UiLinks {

  static final String REL_NOOPENER = "noopener noreferrer";

  private UiLinks() {
  }

  /**
   * Scheme allowlist for anchor hrefs (SEC-L9): {@code http:}/{@code https:}
   * absolute URLs and scheme-less (relative) paths are safe; everything else
   * — {@code javascript:}, {@code data:}, {@code vbscript:}, unknown schemes
   * — is not.
   */
  public static boolean isSafeHref(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    String trimmed = url.strip().toLowerCase(Locale.ROOT);
    int colon = trimmed.indexOf(':');
    if (colon < 0) {
      return true; // relative path — no scheme
    }
    return trimmed.startsWith("http:") || trimmed.startsWith("https:");
  }

  /**
   * Opens {@code url} in a new tab WITH {@code noopener,noreferrer} window
   * features (SEC-L8). {@code Page.open(url, "_blank")} cannot pass window
   * features, so the opened page would keep a {@code window.opener} handle on
   * the app. Unsafe schemes are ignored entirely (SEC-L9).
   */
  public static void openInNewTab(Component context, String url) {
    if (!isSafeHref(url)) {
      return;
    }
    context.getUI().map(UI::getPage).ifPresent(page ->
        page.executeJs("window.open($0, '_blank', 'noopener,noreferrer')", url));
  }

  /**
   * Creates an anchor for {@code url}.  If the URL uses the plain {@code http://}
   * scheme a warning icon is prepended to make the insecure nature immediately
   * visible.  URLs failing the {@link #isSafeHref scheme allowlist} render as
   * plain (non-clickable) text.
   *
   * @param url         target URL (used for href and tooltip)
   * @param displayText text rendered inside the anchor; falls back to {@code url}
   *                    when {@code null}
   * @return a single {@link Anchor} for HTTPS/other URLs; a
   *         {@link HorizontalLayout} (icon + anchor) for HTTP URLs
   */
  public static Component httpAwareLink(String url, String displayText) {
    if (url == null || url.isBlank()) {
      return new Anchor("", displayText != null ? displayText : "");
    }
    final String text = displayText != null ? displayText : url;
    if (!isSafeHref(url)) {
      // SEC-L9: never emit an href for javascript:/data:/... — text only.
      final var inert = new Anchor("", text);
      inert.getElement().setProperty("title", url);
      return inert;
    }
    final var a = externalAnchor(url, text);
    a.getElement().setProperty("title", url);

    if (url.startsWith("http://")) {
      final var warn = new Icon(VaadinIcon.EXCLAMATION_CIRCLE_O);
      warn.addClassName("u-warn-icon");
      warn.getElement().setProperty("title",
          I18n.tr("uilinks.insecureHttp", "⚠ Insecure HTTP – data is not encrypted"));
      final var wrap = new HorizontalLayout(warn, a);
      wrap.setSpacing(false);
      wrap.addClassName("u-gap-4");
      wrap.setAlignItems(Alignment.CENTER);
      return wrap;
    }
    return a;
  }

  /**
   * A {@code _blank} anchor with the SEC-L8 {@code rel} attribute. The caller
   * is responsible for the href being trusted or allowlisted.
   */
  public static Anchor externalAnchor(String url, String text) {
    final var a = new Anchor(url, text);
    a.setTarget("_blank");
    a.getElement().setAttribute("rel", REL_NOOPENER);
    return a;
  }
}
