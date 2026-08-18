package com.svenruppert.urlshortener.ui.vaadin.views.campaign;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.JacksonJson;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignCreateRequest;
import com.svenruppert.urlshortener.core.campaign.CampaignMetaExport;
import com.svenruppert.urlshortener.core.campaign.CampaignStatisticsResponse;
import com.svenruppert.urlshortener.core.campaign.CampaignType;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.urlshortener.ui.vaadin.tools.CampaignClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.svenruppert.urlshortener.ui.vaadin.views.statistics.AggregationGranularity;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Campaign management + a Phase-1 dashboard grundgerüst: create/list/delete
 * campaigns and, for a selected campaign, the bot-excluded rollup (total +
 * per-link placement ranking with share). Statistics correctness rests on the
 * E2E-tested {@code CampaignClient}/server.
 */
@PageTitle("Campaigns")
@Route(value = CampaignsView.PATH, layout = MainLayout.class)
@VisibleFor(AppRole.USER)
public class CampaignsView
    extends VerticalLayout
    implements HasLogger, I18nSupport {

  public static final String PATH = "campaigns";

  private final Grid<Campaign> campaigns = new Grid<>(Campaign.class, false);
  private final Grid<CampaignStatisticsResponse.LinkStat> perLink = new Grid<>(CampaignStatisticsResponse.LinkStat.class, false);
  private final Span total = new Span();
  private final H3 statsTitle = new H3();
  private final DatePicker statsFrom = new DatePicker();
  private final DatePicker statsTo = new DatePicker();
  private final Select<AggregationGranularity> granularity = new Select<>();
  private final HorizontalLayout rangeRow = new HorizontalLayout();
  private final Grid<CampaignStatisticsResponse.DayCount> timeline =
      new Grid<>(CampaignStatisticsResponse.DayCount.class, false);

  private final HorizontalLayout assignRow = new HorizontalLayout();
  private final HorizontalLayout perLinkFilterRow = new HorizontalLayout();
  private final TextField linkUrlFilter = new TextField();
  private final TextField linkTagFilter = new TextField();

  private final URLShortenerClient urlShortenerClient = UrlShortenerClientFactory.newInstance();

  // Raw (unaggregated / unfiltered) statistics for the selected campaign, kept so the
  // granularity selector and the per-link filter can re-derive the displayed views
  // without a server round-trip (timeline) resp. against the campaign members (per-link).
  private List<CampaignStatisticsResponse.DayCount> rawTimeline = List.of();
  private List<CampaignStatisticsResponse.LinkStat> rawPerLink = List.of();

  private Campaign selectedCampaign;

  public CampaignsView() {
    setSpacing(true);
    setPadding(true);
    add(new H2(tr("campaigns.title", "Campaigns")));

    add(buildCreateRow());
    add(buildMetaToolbar());

    campaigns.addColumn(Campaign::name).setHeader(tr("campaigns.name", "Name"));
    campaigns.addColumn(c -> c.type() == null ? "" : c.type().name()).setHeader(tr("campaigns.type", "Type"));
    campaigns.addColumn(c -> c.createdAt() == null ? "" : c.createdAt().toString()).setHeader(tr("campaigns.created", "Created"));
    campaigns.addComponentColumn(this::deleteButton).setHeader("");
    campaigns.setAllRowsVisible(true);
    campaigns.setWidthFull();
    campaigns.asSingleSelect().addValueChangeListener(e -> showStatistics(e.getValue()));
    add(campaigns);

    statsTitle.setVisible(false);
    total.setVisible(false);
    perLink.addColumn(CampaignStatisticsResponse.LinkStat::shortCode).setHeader(tr("campaigns.stats.link", "Short code"));
    perLink.addColumn(ls -> ls.label() == null ? "" : ls.label()).setHeader(tr("campaigns.stats.label", "Placement"));
    perLink.addColumn(CampaignStatisticsResponse.LinkStat::count).setHeader(tr("campaigns.stats.count", "Redirects"));
    perLink.addColumn(ls -> String.format("%.0f%%", ls.share() * 100)).setHeader(tr("campaigns.stats.share", "Share"));
    perLink.addComponentColumn(this::removeButton).setHeader("");
    perLink.setAllRowsVisible(true);
    perLink.setWidthFull();
    perLink.setVisible(false);

    buildRangeRow();
    timeline.addColumn(dc -> dc.date() == null ? "" : dc.date().toString()).setHeader(tr("campaigns.stats.date", "Date"));
    timeline.addColumn(CampaignStatisticsResponse.DayCount::count).setHeader(tr("campaigns.stats.count", "Redirects"));
    timeline.setAllRowsVisible(true);
    timeline.setWidthFull();
    timeline.setVisible(false);

    buildAssignRow();
    buildPerLinkFilterRow();
    // Assigned links first (with their own tag/URL filter), statistics below (per UX request).
    add(assignRow, perLinkFilterRow, perLink, statsTitle, rangeRow, total, timeline);

    refresh();
  }

  /** Free date-range picker for the campaign rollup; re-queries on change. */
  private void buildRangeRow() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    statsFrom.setLabel(tr("campaigns.stats.from", "From"));
    statsTo.setLabel(tr("campaigns.stats.to", "To"));
    statsFrom.setValue(today.minusDays(90)); // set defaults before listeners to avoid re-entry
    statsTo.setValue(today);
    statsFrom.addValueChangeListener(_ -> onRangeChanged());
    statsTo.addValueChangeListener(_ -> onRangeChanged());

    // Aggregation granularity for the timeline, like the Statistics view. The daily
    // timeline is re-bucketed client-side (Day/Week/Month), no extra server call.
    granularity.setLabel(tr("campaigns.stats.granularity", "Granularity"));
    granularity.setItems(AggregationGranularity.DAY, AggregationGranularity.WEEK, AggregationGranularity.MONTH);
    granularity.setItemLabelGenerator(g -> switch (g) {
      case WEEK -> tr("campaigns.stats.gran.week", "Week");
      case MONTH -> tr("campaigns.stats.gran.month", "Month");
      default -> tr("campaigns.stats.gran.day", "Day");
    });
    granularity.setEmptySelectionAllowed(false);
    granularity.setValue(AggregationGranularity.DAY);
    granularity.addValueChangeListener(_ -> applyTimelineGranularity());

    rangeRow.add(statsFrom, statsTo, granularity);
    rangeRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
    rangeRow.setVisible(false);
  }

  private void onRangeChanged() {
    if (selectedCampaign != null) showStatistics(selectedCampaign);
  }

  /** Filter row over the per-link table: restrict shown members by URL and/or tag. */
  private void buildPerLinkFilterRow() {
    linkUrlFilter.setPlaceholder(tr("campaigns.stats.filter.url", "URL contains"));
    linkUrlFilter.setClearButtonVisible(true);
    linkUrlFilter.setValueChangeMode(ValueChangeMode.LAZY);
    linkUrlFilter.addValueChangeListener(_ -> applyPerLinkFilter());

    linkTagFilter.setPlaceholder(tr("campaigns.stats.filter.tag", "Tag"));
    linkTagFilter.setClearButtonVisible(true);
    linkTagFilter.setValueChangeMode(ValueChangeMode.LAZY);
    linkTagFilter.addValueChangeListener(_ -> applyPerLinkFilter());

    perLinkFilterRow.add(linkUrlFilter, linkTagFilter);
    perLinkFilterRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
    perLinkFilterRow.setVisible(false);
  }

  /** Re-buckets the raw daily timeline into the selected granularity. */
  private void applyTimelineGranularity() {
    timeline.setItems(bucketTimeline(rawTimeline, granularity.getValue()));
  }

  private List<CampaignStatisticsResponse.DayCount> bucketTimeline(
      List<CampaignStatisticsResponse.DayCount> daily, AggregationGranularity g) {
    if (g == null || g == AggregationGranularity.DAY) return daily;
    Map<LocalDate, Long> sums = new TreeMap<>();
    for (var dc : daily) {
      if (dc.date() == null) continue;
      LocalDate key = switch (g) {
        case WEEK -> dc.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        case MONTH -> dc.date().withDayOfMonth(1);
        default -> dc.date();
      };
      sums.merge(key, dc.count(), Long::sum);
    }
    return sums.entrySet().stream()
        .map(e -> new CampaignStatisticsResponse.DayCount(e.getKey(), e.getValue()))
        .toList();
  }

  /**
   * Restricts the per-link table to campaign members that also match the URL and/or tag
   * filter — resolved server-side via the same {@link UrlMappingListRequest} the Statistics
   * view uses, then intersected with the current membership.
   */
  private void applyPerLinkFilter() {
    String url = Optional.ofNullable(linkUrlFilter.getValue()).orElse("").trim();
    String tag = Optional.ofNullable(linkTagFilter.getValue()).orElse("").trim();
    if (url.isBlank() && tag.isBlank()) {
      perLink.setItems(rawPerLink);
      return;
    }
    UrlMappingListRequest.Builder b = UrlMappingListRequest.builder().page(1).size(1000);
    if (!url.isBlank()) b.urlPart(url);
    if (!tag.isBlank()) b.tag(tag);
    try {
      Set<String> allowed = urlShortenerClient.list(b.build()).stream()
          .map(ShortUrlMapping::shortCode)
          .collect(Collectors.toSet());
      perLink.setItems(rawPerLink.stream()
          .filter(ls -> allowed.contains(ls.shortCode()))
          .toList());
    } catch (IOException e) {
      logger().info("per-link filter failed: {}", e.getMessage());
      Notifications.errorKey("campaigns.statsFailed", "Could not load statistics.");
    }
  }

  /** Row with a button that opens the search-and-select picker to assign links to the campaign. */
  private void buildAssignRow() {
    Button open = new Button(tr("campaigns.assign.open", "Assign links…"), _ -> {
      if (selectedCampaign != null) {
        new AssignLinksDialog(selectedCampaign, () -> showStatistics(selectedCampaign)).open();
      }
    });
    open.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    assignRow.add(open);
    assignRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
    assignRow.setVisible(false);
  }

  private Button removeButton(CampaignStatisticsResponse.LinkStat ls) {
    Button b = new Button(new Icon(VaadinIcon.CLOSE_SMALL), _ -> removeLink(ls.shortCode()));
    b.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    b.getElement().setAttribute("title", tr("campaigns.remove", "Remove from campaign"));
    return b;
  }

  private void removeLink(String shortCode) {
    if (selectedCampaign == null) return;
    CampaignClientFactory.newInstance().removeLink(selectedCampaign.id(), shortCode)
        .peek(ok -> {
          Notifications.successKey("campaigns.removed", "Link removed from campaign.");
          showStatistics(selectedCampaign);
        })
        .peekFailure(err -> {
          logger().info("removeLink failed: {}", err);
          Notifications.errorKey("campaigns.removeFailed", "Could not remove the link.");
        });
  }

  private HorizontalLayout buildCreateRow() {
    TextField name = new TextField(tr("campaigns.name", "Name"));
    name.setWidth("20em");
    Select<CampaignType> type = new Select<>();
    type.setLabel(tr("campaigns.type", "Type"));
    type.setItems(CampaignType.values());
    type.setValue(CampaignType.OTHER);
    Button create = new Button(tr("campaigns.create", "Create campaign"), _ -> create(name, type));
    create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    HorizontalLayout row = new HorizontalLayout(name, type, create);
    row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
    return row;
  }

  /** Export / import of all campaign metadata (definitions, membership, tags, labels). */
  private HorizontalLayout buildMetaToolbar() {
    Button export = new Button(tr("campaigns.meta.export", "Export metadata"), _ -> openExportDialog());
    export.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    Button importBtn = new Button(tr("campaigns.meta.import", "Import metadata"), _ -> openImportDialog());
    importBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    HorizontalLayout bar = new HorizontalLayout(export, importBtn);
    bar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
    return bar;
  }

  private void openExportDialog() {
    CampaignClientFactory.newInstance().exportMeta()
        .peek(snap -> {
          Dialog dlg = new Dialog();
          dlg.setHeaderTitle(tr("campaigns.meta.export", "Export metadata"));
          dlg.setWidth("48rem");
          TextArea ta = new TextArea();
          ta.setValue(JacksonJson.toJson(snap));
          ta.setReadOnly(true);
          ta.setWidthFull();
          ta.setMinHeight("20rem");
          dlg.add(ta);
          dlg.getFooter().add(new Button(tr("common.close", "Close"), _ -> dlg.close()));
          dlg.open();
        })
        .peekFailure(err -> {
          logger().info("campaign meta export failed: {}", err);
          Notifications.errorKey("campaigns.meta.exportFailed", "Could not export campaign metadata.");
        });
  }

  private void openImportDialog() {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(tr("campaigns.meta.import", "Import metadata"));
    dlg.setWidth("48rem");
    TextArea ta = new TextArea(tr("campaigns.meta.paste", "Paste exported JSON"));
    ta.setWidthFull();
    ta.setMinHeight("18rem");
    Checkbox replace = new Checkbox(tr("campaigns.meta.replace", "Replace existing metadata"));
    dlg.add(ta, replace);
    Button apply = new Button(tr("campaigns.meta.import", "Import metadata"), _ -> {
      String json = ta.getValue() == null ? "" : ta.getValue().trim();
      if (json.isBlank()) {
        Notifications.warningKey("campaigns.meta.pasteRequired", "Paste the exported JSON first.");
        return;
      }
      CampaignMetaExport data;
      try {
        data = JacksonJson.fromJson(json, CampaignMetaExport.class);
      } catch (RuntimeException e) {
        Notifications.errorKey("campaigns.meta.invalidJson", "The pasted JSON is invalid.");
        return;
      }
      CampaignClientFactory.newInstance().importMeta(data, replace.getValue())
          .peek(ok -> {
            Notifications.successKey("campaigns.meta.imported", "Campaign metadata imported.");
            dlg.close();
            refresh();
          })
          .peekFailure(err -> {
            logger().info("campaign meta import failed: {}", err);
            Notifications.errorKey("campaigns.meta.importFailed", "Could not import campaign metadata.");
          });
    });
    apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    dlg.getFooter().add(new Button(tr("common.cancel", "Cancel"), _ -> dlg.close()), apply);
    dlg.open();
  }

  private Button deleteButton(Campaign c) {
    Button b = new Button(tr("campaigns.delete", "Delete"), _ -> delete(c));
    b.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    return b;
  }

  private void create(TextField name, Select<CampaignType> type) {
    String n = name.getValue() == null ? "" : name.getValue().trim();
    if (n.isEmpty()) {
      Notifications.warningKey("campaigns.nameRequired", "A campaign name is required.");
      return;
    }
    CampaignClientFactory.newInstance()
        .create(new CampaignCreateRequest(n, null, type.getValue(), null, null))
        .peek(c -> {
          name.clear();
          refresh();
        })
        .peekFailure(err -> {
          logger().info("campaign create failed: {}", err);
          Notifications.errorKey("campaigns.createFailed", "Could not create the campaign.");
        });
  }

  private void delete(Campaign c) {
    CampaignClientFactory.newInstance().delete(c.id())
        .peek(ok -> refresh())
        .peekFailure(err -> Notifications.errorKey("campaigns.deleteFailed", "Could not delete the campaign."));
  }

  private void refresh() {
    statsTitle.setVisible(false);
    rangeRow.setVisible(false);
    total.setVisible(false);
    timeline.setVisible(false);
    perLink.setVisible(false);
    perLinkFilterRow.setVisible(false);
    assignRow.setVisible(false);
    selectedCampaign = null;
    CampaignClientFactory.newInstance().list()
        .peek(campaigns::setItems)
        .peekFailure(err -> {
          logger().info("campaign list failed: {}", err);
          Notifications.errorKey("campaigns.loadFailed", "Could not load campaigns.");
        });
  }

  private void showStatistics(Campaign c) {
    selectedCampaign = c;
    if (c == null) {
      statsTitle.setVisible(false);
      rangeRow.setVisible(false);
      total.setVisible(false);
      timeline.setVisible(false);
      perLink.setVisible(false);
      perLinkFilterRow.setVisible(false);
      assignRow.setVisible(false);
      return;
    }
    assignRow.setVisible(true);
    rangeRow.setVisible(true);
    LocalDate from = statsFrom.getValue();
    LocalDate to = statsTo.getValue();
    if (from == null || to == null || from.isAfter(to)) {
      Notifications.warningKey("campaigns.stats.rangeInvalid", "Choose a valid date range.");
      return;
    }
    CampaignClientFactory.newInstance().campaignStatistics(c.id(), from, to)
        .peek(stats -> {
          statsTitle.setText(tr("campaigns.stats.title", "Statistics") + " — " + c.name());
          total.setText(tr("campaigns.stats.total", "Total redirects") + ": " + stats.total());
          rawTimeline = stats.timeline() == null ? List.of() : stats.timeline();
          rawPerLink = stats.perLink() == null ? List.of() : stats.perLink();
          applyTimelineGranularity();
          applyPerLinkFilter();
          statsTitle.setVisible(true);
          total.setVisible(true);
          timeline.setVisible(true);
          perLink.setVisible(true);
          perLinkFilterRow.setVisible(true);
        })
        .peekFailure(err -> {
          logger().info("campaign statistics failed: {}", err);
          Notifications.errorKey("campaigns.statsFailed", "Could not load statistics.");
        });
  }
}
