package com.svenruppert.urlshortener.ui.vaadin.components;



import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.views.overview.DetailsDialog;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Global command palette (opened with Ctrl/Cmd-K from anywhere via the app
 * shell) that searches short links by shortcode or target URL and jumps to a
 * hit's details. It reuses the existing server-side paged search
 * ({@link URLShortenerClient#list(UrlMappingListRequest)} over {@code /list});
 * no dedicated search backend is involved. Because the server combines the
 * {@code code} and {@code url} filters with AND, a "shortcode OR URL" match is
 * obtained by two capped queries whose results are merged.
 *
 * <p>Tag/label search is intentionally out of scope here — those fields live
 * only in the enterprise link-meta plug-in and need their own endpoint; this
 * palette stays edition-neutral over shortcode + URL.
 */
@com.vaadin.flow.component.dependency.CssImport("./styles/quick-search.css")
public class QuickSearchDialog
    extends Dialog
    implements HasLogger, I18nSupport {

  private static final int MAX_RESULTS = 8;
  private static final int DEBOUNCE_MS = 250;
  private static final int MIN_CHARS = 2;

  private static final String C_ROOT = "js-quicksearch";
  private static final String C_HEAD = "js-quicksearch__head";
  private static final String C_RESULTS = "js-quicksearch__results";
  private static final String C_ROW = "js-quicksearch__row";

  private static final String K_PLACEHOLDER = "quicksearch.placeholder";
  private static final String K_HINT = "quicksearch.hint";
  private static final String K_EMPTY = "quicksearch.empty";
  private static final String K_ERROR = "quicksearch.error";
  private static final String K_ACTIVE = "quicksearch.active";
  private static final String K_INACTIVE = "quicksearch.inactive";
  private static final String K_ARIA = "quicksearch.aria";

  private final URLShortenerClient client = UrlShortenerClientFactory.newInstance();
  private final TextField input = new TextField();
  private final VerticalLayout results = new VerticalLayout();

  private List<ShortUrlMapping> lastHits = List.of();

  public QuickSearchDialog() {
    addClassName(C_ROOT);
    setModal(true);
    setDraggable(false);
    setResizable(false);
    setCloseOnEsc(true);
    setCloseOnOutsideClick(true);
    setWidth("min(560px, 92vw)");
    getElement().setAttribute("aria-label", tr(K_ARIA, "Quick search"));

    input.setPlaceholder(tr(K_PLACEHOLDER, "Search links by shortcode or URL…"));
    input.setPrefixComponent(VaadinIcon.SEARCH.create());
    input.setClearButtonVisible(true);
    input.setWidthFull();
    input.setValueChangeMode(ValueChangeMode.LAZY);
    input.setValueChangeTimeout(DEBOUNCE_MS);
    input.addValueChangeListener(e -> runSearch(e.getValue()));
    input.addKeyDownListener(Key.ENTER, _ -> openFirst());

    HorizontalLayout head = new HorizontalLayout(input);
    head.addClassName(C_HEAD);
    head.setWidthFull();
    head.setAlignItems(Alignment.CENTER);

    results.addClassName(C_RESULTS);
    results.setPadding(false);
    results.setSpacing(false);
    results.setWidthFull();

    VerticalLayout body = new VerticalLayout(head, results);
    body.setPadding(false);
    body.setSpacing(false);
    add(body);

    showMessage(tr(K_HINT, "Type at least 2 characters."));

    addOpenedChangeListener(e -> {
      if (e.isOpened()) input.focus();
    });
  }

  private void runSearch(String raw) {
    String q = raw == null ? "" : raw.trim();
    lastHits = List.of();
    if (q.length() < MIN_CHARS) {
      showMessage(tr(K_HINT, "Type at least 2 characters."));
      return;
    }
    try {
      // The server ANDs the code and url filters, so two capped queries are
      // merged to realize a "shortcode OR target URL" match.
      var merged = new LinkedHashMap<String, ShortUrlMapping>();
      for (ShortUrlMapping m : client.list(query(q, true))) {
        merged.putIfAbsent(m.shortCode(), m);
      }
      for (ShortUrlMapping m : client.list(query(q, false))) {
        merged.putIfAbsent(m.shortCode(), m);
      }
      lastHits = merged.values().stream().limit(MAX_RESULTS).toList();
      if (lastHits.isEmpty()) {
        showMessage(tr(K_EMPTY, "No matches."));
        return;
      }
      results.removeAll();
      for (ShortUrlMapping m : lastHits) {
        results.add(resultRow(m));
      }
    } catch (IOException ex) {
      logger().warn("quick-search failed: {}", ex.getMessage());
      showMessage(tr(K_ERROR, "Search failed."));
    }
  }

  private static UrlMappingListRequest query(String q, boolean byCode) {
    UrlMappingListRequest.Builder b = UrlMappingListRequest.builder()
        .size(MAX_RESULTS)
        .sort("createdAt")
        .dir("desc");
    if (byCode) {
      b.codePart(q);
    } else {
      b.urlPart(q);
    }
    return b.build();
  }

  private Component resultRow(ShortUrlMapping m) {
    Span title = new Span(m.shortCode());
    title.addClassName("js-row-title");
    Span meta = new Span(m.originalUrl());
    meta.addClassName("js-row-meta");
    Div main = new Div(title, meta);
    main.addClassName("js-row-main");

    Span state = new Span(m.active() ? tr(K_ACTIVE, "active") : tr(K_INACTIVE, "inactive"));
    state.addClassName("js-pill");
    state.addClassName(m.active() ? "js-pill--success" : "js-pill--neutral");

    Div row = new Div(main, state);
    row.addClassName("js-row");
    row.addClassName(C_ROW);
    row.getElement().setAttribute("role", "button");
    row.addClickListener(_ -> open(m));
    return row;
  }

  private void openFirst() {
    if (!lastHits.isEmpty()) {
      open(lastHits.get(0));
    }
  }

  private void open(ShortUrlMapping m) {
    close();
    new DetailsDialog(client, m).open();
  }

  private void showMessage(String text) {
    results.removeAll();
    Div empty = new Div(new Span(text));
    empty.addClassName("js-empty");
    results.add(empty);
  }
}
