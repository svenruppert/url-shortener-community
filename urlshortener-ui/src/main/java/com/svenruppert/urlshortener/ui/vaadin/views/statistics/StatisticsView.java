package com.svenruppert.urlshortener.ui.vaadin.views.statistics;



import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.urlshortener.client.StatisticsClient;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.statistics.StatisticsCountResponse;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.urlshortener.ui.vaadin.components.SearchBar;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.tools.StatisticsClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * Statistics overview view showing all shortcodes with their redirect counts.
 * Uses SearchBar for filtering and StatisticsToolbar for date range/granularity.
 */
@PageTitle("Statistics")
@Route(value = StatisticsView.PATH, layout = MainLayout.class)
@VisibleFor(AppRole.USER)
@CssImport("./styles/statistics-view.css")
public class StatisticsView
    extends VerticalLayout
    implements HasLogger, I18nSupport {

  public static final String PATH = "statistics";

  private static final String C_ROOT = "statistics-view";
  private static final String C_GRID = "statistics-view__grid";
  private static final String C_URL_ANCHOR = "statistics-view__url";
  private static final String C_SHORTCODE = "statistics-view__shortcode";
  private static final String C_COUNT = "statistics-view__count";
  private static final String C_PAGING_BAR = "statistics-view__pagingbar";

  // i18n keys
  private static final String K_TITLE = "statistics.title";
  private static final String K_COL_SHORTCODE = "statistics.grid.column.shortcode";
  private static final String K_COL_URL = "statistics.grid.column.url";
  private static final String K_COL_COUNT = "statistics.grid.column.count";
  private static final String K_COL_ACTIONS = "statistics.grid.column.actions";
  private static final String K_PAGING_PREV = "statistics.paging.prev";
  private static final String K_PAGING_NEXT = "statistics.paging.next";
  private static final String K_DETAILS_TOOLTIP = "statistics.details.tooltip";
  private static final String K_EXPORT = "statistics.export";
  private static final String K_EXPORT_ANCHOR = "statistics.export.anchor";
  private static final String K_EXPORT_TOOLTIP = "statistics.export.tooltip";
  private static final String K_IMPORT = "statistics.import";
  private static final String K_IMPORT_TOOLTIP = "statistics.import.tooltip";
  private static final String K_DOWNLOAD_STARTED = "statistics.export.started";
  private static final String K_DOWNLOAD_COMPLETED = "statistics.export.completed";
  private static final String K_DOWNLOAD_FAILED = "statistics.export.failed";

  private final URLShortenerClient urlShortenerClient = UrlShortenerClientFactory.newInstance();
  private final StatisticsClient statisticsClient = StatisticsClientFactory.newInstance();

  private final SearchBar searchBar = new SearchBar();
  private final StatisticsToolbar statisticsToolbar = new StatisticsToolbar();
  private final Grid<ShortUrlMappingWithCount> grid = new Grid<>();

  private final Button prevBtn = new Button();
  private final Button nextBtn = new Button();
  private final Text pageInfo = new Text("");

  private final Button btnExport = new Button();
  private final Button btnImport = new Button();
  private final Anchor exportAnchor = initExportAnchor();

  private int currentPage = 1;
  private int totalCount = 0;

  public StatisticsView() {
    addClassName(C_ROOT);
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(tr(K_TITLE, "Statistics")));

    configureSearchBar();
    configureStatisticsToolbar();
    configureGrid();
    configurePagingBar();
    configureExportImport();
    applyI18n();

    add(buildExportImportBar(), searchBar, statisticsToolbar, grid, buildPagingBar());

    // Initial data load
    loadData();
  }

  private Anchor initExportAnchor() {
    DownloadHandler handler = DownloadHandler.fromInputStream(_ -> {
      LocalDate from = statisticsToolbar.getFromDate();
      LocalDate to = statisticsToolbar.getToDate();
      StatisticsClient.ExportZipDownload download =
          statisticsClient.exportAsZipDownload(from, to, java.util.Set.of());
      return new DownloadResponse(download.inputStreamFactory().get(),
                                  download.filename(),
                                  MediaType.APPLICATION_ZIP.mime(),
                                  -1);
    })
        .whenStart(() ->
                       Notification.show(tr(K_DOWNLOAD_STARTED, "Download started"),
                                         3000, Notification.Position.BOTTOM_START))
        .whenComplete(success -> {
          if (success) {
            Notification.show(tr(K_DOWNLOAD_COMPLETED, "Download completed"),
                              3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          } else {
            Notification.show(tr(K_DOWNLOAD_FAILED, "Download failed"),
                              3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });

    Anchor a = new Anchor(handler, "");
    a.getElement().setAttribute("download", true);
    a.addClassName("u-hidden");
    return a;
  }

  private void configureExportImport() {
    btnExport.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    btnExport.addClickListener(_ -> triggerExportDownload());

    btnImport.addClickListener(_ -> openImportDialog());
  }

  private void triggerExportDownload() {
    try {
      exportAnchor.getElement().callJsFunction("click");
    } catch (Exception ex) {
      logger().warn("Statistics export failed", ex);
      Notifications.operationFailed(ex);
    }
  }

  private void openImportDialog() {
    new StatisticsImportDialog(statisticsClient, this::loadData).open();
  }

  private HorizontalLayout buildExportImportBar() {
    HorizontalLayout bar = new HorizontalLayout(btnExport, btnImport, exportAnchor);
    bar.setAlignItems(FlexComponent.Alignment.CENTER);
    return bar;
  }

  private void configureSearchBar() {
    // Configure callbacks
    searchBar.setOnFilterChange(_ -> {
      currentPage = 1;
      loadData();
    });
    searchBar.setOnPageSizeChange(_ -> {
      currentPage = 1;
      loadData();
    });
    searchBar.setOnReset(_ -> {
      currentPage = 1;
      statisticsToolbar.resetToDefaults();
      loadData();
    });

    // Set default page size
    searchBar.setPageSize(25);
  }

  private void configureStatisticsToolbar() {
    // Hide granularity in overview (only needed in detail view)
    statisticsToolbar.hideGranularity();

    statisticsToolbar.setOnFilterChange(_ -> {
      currentPage = 1;
      loadData();
    });
  }

  private void applyI18n() {
    prevBtn.setText(tr(K_PAGING_PREV, "Previous"));
    nextBtn.setText(tr(K_PAGING_NEXT, "Next"));

    btnExport.setText(tr(K_EXPORT, "Export"));
    btnExport.getElement().setAttribute("title",
        tr(K_EXPORT_TOOLTIP, "Download all redirect events for the current date range as ZIP"));
    btnImport.setText(tr(K_IMPORT, "Import"));
    btnImport.getElement().setAttribute("title",
        tr(K_IMPORT_TOOLTIP, "Import a statistics ZIP and rebuild aggregates"));
    exportAnchor.setText(tr(K_EXPORT_ANCHOR, "Download"));

    grid.getColumnByKey("shortcode").setHeader(tr(K_COL_SHORTCODE, "Shortcode"));
    grid.getColumnByKey("url").setHeader(tr(K_COL_URL, "Original URL"));
    grid.getColumnByKey("count").setHeader(tr(K_COL_COUNT, "Redirects"));
    grid.getColumnByKey("actions").setHeader(tr(K_COL_ACTIONS, ""));
  }

  private void configureGrid() {
    grid.addClassName(C_GRID);
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
    grid.setSelectionMode(Grid.SelectionMode.NONE);

    // Shortcode column
    grid.addComponentColumn(item -> {
      Span code = new Span(item.mapping().shortCode());
      code.addClassName(C_SHORTCODE);
      return code;
    }).setKey("shortcode").setAutoWidth(true).setFlexGrow(0).setSortable(true);

    // URL column
    grid.addComponentColumn(item -> {
      String url = item.mapping().originalUrl();
      // SEC-L8/L9 (V00.17.00 P090): noopener rel + scheme allowlist — an
      // unsafe scheme renders as inert text instead of a clickable href.
      Anchor a = com.svenruppert.urlshortener.ui.vaadin.tools.UiLinks.isSafeHref(url)
          ? com.svenruppert.urlshortener.ui.vaadin.tools.UiLinks.externalAnchor(url, url)
          : new Anchor("", url);
      a.addClassName(C_URL_ANCHOR);
      a.getElement().setProperty("title", url);
      return a;
    }).setKey("url").setFlexGrow(1).setSortable(true);

    // Count column — statistics-derived (not a backend list sort key), so not header-sortable.
    grid.addComponentColumn(item -> {
      Span count = new Span(String.valueOf(item.count()));
      count.addClassName(C_COUNT);
      return count;
    }).setKey("count").setAutoWidth(true).setFlexGrow(0);

    // Actions column (detail link)
    grid.addComponentColumn(item -> {
      Button detailBtn = new Button(new Icon(VaadinIcon.CHART));
      detailBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
      detailBtn.getElement().setProperty("title", tr(K_DETAILS_TOOLTIP, "View details"));
      detailBtn.addClickListener(_ ->
                                     detailBtn.getUI()
                                         .ifPresent(ui ->
                                                        ui.navigate(StatisticsDetailView.class,
                                                                    item.mapping().shortCode())
                                         )
      );
      return detailBtn;
    }).setKey("actions").setAutoWidth(true).setFlexGrow(0);

    // Header sorting drives the backend query (data is server-paged). The SearchBar sort
    // comboboxes are the single source of truth, so the header writes its selection back
    // into them (refresh suppressed to avoid a double load), then reloads once.
    grid.addSortListener(e -> {
      var order = e.getSortOrder();
      String field = "createdAt";
      String direction = "desc";
      if (!order.isEmpty()) {
        var o = order.get(0);
        direction = o.getDirection() == SortDirection.ASCENDING ? "asc" : "desc";
        field = switch (o.getSorted().getKey()) {
          case "shortcode" -> "shortCode";
          case "url" -> "originalUrl";
          default -> "createdAt";
        };
      }
      searchBar.setRefreshSuppressed(true);
      try {
        searchBar.setSortBy(field);
        searchBar.setDirValue(direction);
      } finally {
        searchBar.setRefreshSuppressed(false);
      }
      currentPage = 1;
      loadData();
    });
  }

  private void loadData() {
    int pageSize = Optional.ofNullable(searchBar.getPageSize()).orElse(25);

    // First, get total count for paging info
    try {
      UrlMappingListRequest countReq = buildRequest(null, null);
      logger().info(" loadData - countReq {}", countReq);
      totalCount = urlShortenerClient.listCount(countReq);
    } catch (IOException ex) {
      logger().error("Error counting mappings", ex);
      totalCount = 0;
    }

    // Adjust currentPage if needed
    int maxPage = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
    currentPage = Math.min(Math.max(1, currentPage), maxPage);

    // Load current page data
    List<ShortUrlMappingWithCount> items;
    try {
      UrlMappingListRequest req = buildRequest(currentPage, pageSize);
      List<ShortUrlMapping> mappings = urlShortenerClient.list(req);
      items = enrichWithCounts(mappings);
    } catch (IOException ex) {
      logger().error("Error fetching mappings (page={}, size={})", currentPage, pageSize, ex);
      Notifications.loadingFailed();
      items = Collections.emptyList();
    }

    grid.setItems(items);
    refreshPageInfo();
  }

  private UrlMappingListRequest buildRequest(Integer page, Integer size) {
    UrlMappingListRequest.Builder b = UrlMappingListRequest.builder();

    String codePart = searchBar.getCodePart();
    String urlPart = searchBar.getUrlPart();
    String tagPart = searchBar.getTagPart();
    if (!codePart.isBlank()) {
      b.codePart(codePart);
    }
    if (!urlPart.isBlank()) {
      b.urlPart(urlPart);
    }
    if (!tagPart.isBlank()) {
      b.tag(tagPart);
    }

    // Sort/direction come from the SearchBar comboboxes (single source of truth); the grid
    // header keeps them in sync. Fall back to the default ordering when unset.
    b.sort(Optional.ofNullable(searchBar.getSortBy()).orElse("createdAt"))
        .dir(Optional.ofNullable(searchBar.getDirValue()).orElse("desc"));

    if (page != null && size != null) {
      b.page(page).size(size);
    }

    return b.build();
  }

  private List<ShortUrlMappingWithCount> enrichWithCounts(List<ShortUrlMapping> mappings) {
    LocalDate from = statisticsToolbar.getFromDate();
    LocalDate to = statisticsToolbar.getToDate();

    return mappings.stream()
        .map(mapping -> {
          long count = 0;
          try {
            if (from != null && to != null) {
              StatisticsCountResponse response = statisticsClient.getCountForDateRange(
                  mapping.shortCode(), from, to
              );
              count = response != null ? response.count() : 0;
            } else {
              StatisticsCountResponse response = statisticsClient.getTotalCount(mapping.shortCode());
              count = response != null ? response.count() : 0;
            }
          } catch (IOException ex) {
            logger().warn("Failed to get count for {}: {}", mapping.shortCode(), ex.getMessage());
          }
          return new ShortUrlMappingWithCount(mapping, count);
        })
        .toList();
  }

  private HorizontalLayout buildPagingBar() {
    prevBtn.addClickListener(_ -> {
      if (currentPage > 1) {
        currentPage--;
        loadData();
      }
    });

    nextBtn.addClickListener(_ -> {
      int pageSize = Optional.ofNullable(searchBar.getPageSize()).orElse(25);
      int maxPage = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
      if (currentPage < maxPage) {
        currentPage++;
        loadData();
      }
    });

    HorizontalLayout pagingBar = new HorizontalLayout(prevBtn, nextBtn, pageInfo);
    pagingBar.addClassName(C_PAGING_BAR);
    pagingBar.setAlignItems(FlexComponent.Alignment.CENTER);
    return pagingBar;
  }

  private void configurePagingBar() {
    prevBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    nextBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
  }

  private void refreshPageInfo() {
    int pageSize = Optional.ofNullable(searchBar.getPageSize()).orElse(25);
    int maxPage = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));

    pageInfo.setText(tr(
        "statistics.paging.info",
        "Page {0} / {1} • {2} total",
        currentPage, maxPage, totalCount
    ));

    prevBtn.setEnabled(currentPage > 1);
    nextBtn.setEnabled(currentPage < maxPage);
  }

  /**
   * Record combining a ShortUrlMapping with its redirect count.
   */
  public record ShortUrlMappingWithCount(ShortUrlMapping mapping, long count) {
  }
}