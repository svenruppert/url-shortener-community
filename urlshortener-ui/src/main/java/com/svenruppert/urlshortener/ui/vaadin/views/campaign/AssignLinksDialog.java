package com.svenruppert.urlshortener.ui.vaadin.views.campaign;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.campaign.Campaign;
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
import com.vaadin.flow.component.textfield.TextField;

import java.io.IOException;
import java.util.List;

/**
 * Picker to assign several existing links to a campaign at once: a full search
 * bar (shortcode / URL / tag / date filters) drives a multi-select result table;
 * the selected links are assigned in one batch request (with an optional shared
 * placement label) via {@code CampaignClient.assignLinks}.
 */
public final class AssignLinksDialog
    extends Dialog
    implements HasLogger, I18nSupport {

  private static final int MAX_RESULTS = 500;

  private final Campaign campaign;
  private final Runnable onAssigned;
  private final SearchBar searchBar = new SearchBar();
  private final Grid<ShortUrlMapping> grid = new Grid<>(ShortUrlMapping.class, false);
  private final TextField label = new TextField();
  private final URLShortenerClient client = UrlShortenerClientFactory.newInstance();

  public AssignLinksDialog(Campaign campaign, Runnable onAssigned) {
    this.campaign = campaign;
    this.onAssigned = onAssigned;

    setHeaderTitle(tr("campaigns.assign.dialogTitle", "Assign links to “{0}”", campaign.name()));
    setWidth("60rem");
    setResizable(true);
    setDraggable(true);

    grid.setSelectionMode(Grid.SelectionMode.MULTI);
    grid.addThemeVariants(GridVariant.LUMO_COMPACT);
    grid.addColumn(ShortUrlMapping::shortCode).setHeader(tr("tags.col.shortcode", "Short code")).setAutoWidth(true);
    grid.addColumn(ShortUrlMapping::originalUrl).setHeader(tr("tags.col.url", "URL"));
    grid.setAllRowsVisible(false);
    grid.setHeight("22rem");
    grid.setWidthFull();

    label.setLabel(tr("campaigns.assign.label", "Placement label (optional)"));
    label.setWidthFull();

    searchBar.setOnFilterChange(_ -> reload());
    searchBar.setOnReset(_ -> reload());

    add(searchBar, grid, label);

    Button assign = new Button(tr("campaigns.assign.assignSelected", "Assign selected"), _ -> assignSelected());
    assign.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    Button cancel = new Button(tr("common.cancel", "Cancel"), _ -> close());
    getFooter().add(cancel, assign);

    reload();
  }

  private void reload() {
    try {
      List<ShortUrlMapping> links = client.list(searchBar.buildFilter(1, MAX_RESULTS));
      grid.setItems(links);
    } catch (IOException e) {
      logger().info("could not load links for assignment: {}", e.getMessage());
      Notifications.errorKey("campaigns.assignFailed", "Could not assign the link.");
    }
  }

  private void assignSelected() {
    var selected = grid.getSelectedItems();
    if (selected.isEmpty()) {
      Notifications.warningKey("campaigns.assign.selectRequired", "Select a link to assign.");
      return;
    }
    List<String> codes = selected.stream().map(ShortUrlMapping::shortCode).toList();
    String placement = label.getValue() == null ? "" : label.getValue().trim();
    CampaignClientFactory.newInstance()
        .assignLinks(campaign.id(), codes, placement.isBlank() ? null : placement)
        .peek(resp -> {
          Notifications.successKey("campaigns.assignedBatch", "Assigned {0} link(s), skipped {1}.",
              resp.assigned(), resp.skipped());
          close();
          if (onAssigned != null) onAssigned.run();
        })
        .peekFailure(err -> {
          logger().info("batch assign failed: {}", err);
          Notifications.errorKey("campaigns.assignFailed", "Could not assign the link.");
        });
  }
}
