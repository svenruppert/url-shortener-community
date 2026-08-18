package com.svenruppert.urlshortener.ui.vaadin.views.tag;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.ui.vaadin.components.SearchBar;
import com.svenruppert.urlshortener.ui.vaadin.tools.CampaignClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;

import java.io.IOException;
import java.util.List;

/**
 * Picker to add a tag to several existing links at once: a full search bar drives
 * a multi-select result table; the chosen tag is added (union) to every selected
 * link via {@code CampaignClient.assignTagToLinks}.
 */
public final class AssignTagDialog
    extends Dialog
    implements HasLogger, I18nSupport {

  private static final int MAX_RESULTS = 500;

  private final String tag;
  private final Runnable onAssigned;
  private final SearchBar searchBar = new SearchBar();
  private final Grid<ShortUrlMapping> grid = new Grid<>(ShortUrlMapping.class, false);
  private final URLShortenerClient client = UrlShortenerClientFactory.newInstance();

  public AssignTagDialog(String tag, Runnable onAssigned) {
    this.tag = tag;
    this.onAssigned = onAssigned;

    setHeaderTitle(tr("tags.assign.dialogTitle", "Assign links to tag “{0}”", tag));
    setWidth("60rem");
    setResizable(true);
    setDraggable(true);

    grid.setSelectionMode(Grid.SelectionMode.MULTI);
    grid.addThemeVariants(GridVariant.LUMO_COMPACT);
    grid.addColumn(ShortUrlMapping::shortCode).setHeader(tr("tags.col.shortcode", "Short code")).setAutoWidth(true);
    grid.addColumn(ShortUrlMapping::originalUrl).setHeader(tr("tags.col.url", "URL"));
    grid.setHeight("22rem");
    grid.setWidthFull();

    searchBar.setOnFilterChange(_ -> reload());
    searchBar.setOnReset(_ -> reload());
    add(searchBar, grid);

    Button assign = new Button(tr("tags.assign.assignSelected", "Assign selected"), _ -> assignSelected());
    assign.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    Button cancel = new Button(tr("common.cancel", "Cancel"), _ -> close());
    getFooter().add(cancel, assign);

    reload();
  }

  private void reload() {
    try {
      grid.setItems(client.list(searchBar.buildFilter(1, MAX_RESULTS)));
    } catch (IOException e) {
      logger().info("could not load links for tag assignment: {}", e.getMessage());
      Notifications.errorKey("tags.assignFailed", "Could not assign the tag.");
    }
  }

  private void assignSelected() {
    var selected = grid.getSelectedItems();
    if (selected.isEmpty()) {
      Notifications.warningKey("tags.assign.selectRequired", "Select at least one link.");
      return;
    }
    List<String> codes = selected.stream().map(ShortUrlMapping::shortCode).toList();
    CampaignClientFactory.newInstance().assignTagToLinks(tag, codes)
        .peek(ok -> {
          Notifications.successKey("tags.assigned", "Tag assigned to {0} link(s).", codes.size());
          close();
          if (onAssigned != null) onAssigned.run();
        })
        .peekFailure(err -> {
          logger().info("assignTagToLinks failed: {}", err);
          Notifications.errorKey("tags.assignFailed", "Could not assign the tag.");
        });
  }
}
