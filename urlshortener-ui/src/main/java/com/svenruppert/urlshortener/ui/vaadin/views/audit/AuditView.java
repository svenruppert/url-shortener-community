package com.svenruppert.urlshortener.ui.vaadin.views.audit;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.AuditClient;
import com.svenruppert.urlshortener.core.auth.AuditEntry;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.urlshortener.ui.vaadin.tools.AuditClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Admin audit-log view (PRE-REBUILD-SCOPE Kap. 3.10): a grid of
 * security audit events with a subject filter and a reload button.
 * Talks to the server through {@link AuditClient} (session Bearer
 * token attached by {@link AuditClientFactory}).
 */
@PageTitle("Audit log")
@Route(value = AuditView.PATH, layout = MainLayout.class)
@VisibleFor(AppRole.ADMIN)
public class AuditView
    extends VerticalLayout
    implements HasLogger, I18nSupport {

  public static final String PATH = "audit";

  private static final String K_TITLE = "audit.title";
  private static final String K_SUBJECT = "audit.subject";
  private static final String K_RELOAD = "audit.reload";
  private static final String K_COL_TIME = "audit.col.time";
  private static final String K_COL_TYPE = "audit.col.type";
  private static final String K_COL_SUBJECT = "audit.col.subject";
  private static final String K_COL_DETAIL = "audit.col.detail";
  private static final String K_LOAD_FAILED = "audit.loadFailed";

  private static final int DEFAULT_LIMIT = 500;

  private final AuditClient auditClient = AuditClientFactory.newInstance();
  private final Grid<AuditEntry> grid = new Grid<>(AuditEntry.class, false);
  private final TextField subjectFilter = new TextField();

  public AuditView() {
    setSizeFull();
    setPadding(true);

    // jSentinel brand accent (lime-gold) at the top of the view.
    var brandBar = new com.vaadin.flow.component.html.Div();
    brandBar.addClassName("js-brand-bar");
    add(brandBar);
    add(new H2(tr(K_TITLE, "Audit log")));

    subjectFilter.setPlaceholder(tr(K_SUBJECT, "Subject filter"));
    subjectFilter.setClearButtonVisible(true);
    Button reload = new Button(tr(K_RELOAD, "Reload"), _ -> reload());
    reload.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    HorizontalLayout toolbar = new HorizontalLayout(subjectFilter, reload);
    toolbar.setSpacing(true);
    add(toolbar);

    grid.addColumn(AuditEntry::timestamp).setHeader(tr(K_COL_TIME, "Timestamp")).setAutoWidth(true);
    grid.addColumn(AuditEntry::type).setHeader(tr(K_COL_TYPE, "Type")).setAutoWidth(true);
    grid.addColumn(AuditEntry::subject).setHeader(tr(K_COL_SUBJECT, "Subject")).setAutoWidth(true);
    grid.addColumn(AuditEntry::detail).setHeader(tr(K_COL_DETAIL, "Detail")).setFlexGrow(1);
    grid.setSizeFull();
    add(grid);

    reload();
  }

  private void reload() {
    String subject = subjectFilter.getValue();
    auditClient.fetch(subject == null || subject.isBlank() ? null : subject, DEFAULT_LIMIT)
        .peek(grid::setItems)
        .peekFailure(error -> {
          logger().info("audit fetch failed: {}", error);
          Notifications.errorKey(K_LOAD_FAILED, "Could not load the audit log.");
        });
  }
}
