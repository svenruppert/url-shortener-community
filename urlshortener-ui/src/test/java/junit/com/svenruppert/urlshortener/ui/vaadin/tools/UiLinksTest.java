package junit.com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.urlshortener.ui.vaadin.tools.UiLinks;
import com.vaadin.flow.component.html.Anchor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V00.17.00 P090 — SEC-L8 (every {@code _blank} anchor carries
 * {@code rel="noopener noreferrer"}) and SEC-L9 (scheme allowlist: a stored
 * {@code javascript:}/{@code data:} URL never becomes a clickable href).
 * Vaadin components are instantiable without a UI context, so this stays a
 * plain unit test.
 */
class UiLinksTest {

  @Test
  void allowlistAcceptsHttpHttpsAndRelative() {
    assertTrue(UiLinks.isSafeHref("https://example.org/x"));
    assertTrue(UiLinks.isSafeHref("http://example.org"));
    assertTrue(UiLinks.isSafeHref("HTTPS://EXAMPLE.ORG"), "scheme check is case-insensitive");
    assertTrue(UiLinks.isSafeHref("/relative/path"));
  }

  @Test
  void allowlistRejectsActiveContentSchemes() {
    assertFalse(UiLinks.isSafeHref("javascript:alert(1)"), "SEC-L9");
    assertFalse(UiLinks.isSafeHref("  JavaScript:alert(1)"), "trim + case");
    assertFalse(UiLinks.isSafeHref("data:text/html;base64,xx"));
    assertFalse(UiLinks.isSafeHref("vbscript:x"));
    assertFalse(UiLinks.isSafeHref(null));
    assertFalse(UiLinks.isSafeHref("  "));
  }

  @Test
  void externalAnchorCarriesNoopenerRel() {
    Anchor a = UiLinks.externalAnchor("https://example.org", "x");
    assertEquals("_blank", a.getElement().getAttribute("target"));
    assertEquals("noopener noreferrer", a.getElement().getAttribute("rel"), "SEC-L8");
  }

  @Test
  void httpAwareLinkKeepsRelAndNeutralizesUnsafeSchemes() {
    var safe = UiLinks.httpAwareLink("https://example.org", null);
    assertInstanceOf(Anchor.class, safe);
    assertEquals("noopener noreferrer", ((Anchor) safe).getElement().getAttribute("rel"));

    var unsafe = UiLinks.httpAwareLink("javascript:alert(1)", "click me");
    assertInstanceOf(Anchor.class, unsafe);
    String href = ((Anchor) unsafe).getHref();
    assertTrue(href == null || href.isBlank(),
        "SEC-L9: unsafe scheme must render without an href, got: " + href);
  }
}
