package com.svenruppert.urlshortener.ui.vaadin.views.tag;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.URLShortenerClient;
import com.svenruppert.urlshortener.core.campaign.TagInfo;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;
import com.svenruppert.urlshortener.core.urlmapping.UrlMappingListRequest;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.CurrentUser;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.urlshortener.ui.vaadin.tools.CampaignClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.tools.UrlShortenerClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Central tag management page: lists every distinct tag with its link count and
 * lets the operator rename (merge) or delete a tag across all links, plus drill
 * into the links carrying a selected tag. Backed by the E2E-tested tag endpoints
 * ({@code CampaignClient.listTags/renameTag/deleteTag}).
 */
@PageTitle("Tags")
@Route(value = TagsView.PATH, layout = MainLayout.class)
@VisibleFor(AppRole.USER)
public class TagsView
    extends VerticalLayout
    implements HasLogger, I18nSupport {

  public static final String PATH = "tags";

  private final Grid<TagInfo> tagsGrid = new Grid<>(TagInfo.class, false);
  private final Grid<ShortUrlMapping> linksGrid = new Grid<>(ShortUrlMapping.class, false);
  private final H3 linksTitle = new H3();
  private final Span emptyHint = new Span();

  public TagsView() {
    setSpacing(true);
    setPadding(true);
    add(new H2(tr("tags.title", "Tags")));
    // Registry mutations are admin-only (V00.17.00 P060, concept DECISION 2);
    // server-side enforced in TagsHandler — this only hides the dead controls.
    if (CurrentUser.isAdmin()) {
      add(buildCreateRow());
    }

    tagsGrid.addColumn(TagInfo::tag).setHeader(tr("tags.col.tag", "Tag")).setAutoWidth(true);
    tagsGrid.addColumn(TagInfo::linkCount).setHeader(tr("tags.col.count", "Links")).setAutoWidth(true);
    tagsGrid.addComponentColumn(this::rowActions).setHeader("");
    tagsGrid.setAllRowsVisible(true);
    tagsGrid.setWidthFull();
    tagsGrid.asSingleSelect().addValueChangeListener(e -> showLinksOfTag(e.getValue()));

    emptyHint.setText(tr("tags.empty",
        "No tags yet. Tags are created by tagging a link — in its details dialog or when creating links. "
            + "Once links carry tags, they appear here to rename, merge or delete."));
    emptyHint.getStyle().set("color", "var(--lumo-secondary-text-color)");
    emptyHint.setVisible(false);
    add(tagsGrid, emptyHint);

    linksTitle.setVisible(false);
    linksGrid.addColumn(ShortUrlMapping::shortCode).setHeader(tr("tags.col.shortcode", "Short code")).setAutoWidth(true);
    linksGrid.addColumn(ShortUrlMapping::originalUrl).setHeader(tr("tags.col.url", "URL"));
    linksGrid.setAllRowsVisible(true);
    linksGrid.setWidthFull();
    linksGrid.setVisible(false);
    add(linksTitle, linksGrid);

    refresh();
  }

  /** Row to define a new (empty) tag up front, so it can be assigned to links later. */
  private HorizontalLayout buildCreateRow() {
    TextField name = new TextField(tr("tags.create.label", "New tag"));
    name.setPlaceholder(tr("tags.create.placeholder", "e.g. print"));
    name.setWidth("18em");
    Button create = new Button(tr("tags.create.button", "Create tag"), _ -> {
      String n = name.getValue() == null ? "" : name.getValue().trim().toLowerCase(Locale.ROOT);
      if (n.isBlank()) {
        Notifications.warningKey("tags.create.nameRequired", "Enter a tag name.");
        return;
      }
      CampaignClientFactory.newInstance().createTag(n)
          .peek(ok -> {
            name.clear();
            Notifications.successKey("tags.created", "Tag created.");
            refresh();
          })
          .peekFailure(err -> {
            logger().info("createTag failed: {}", err);
            Notifications.errorKey("tags.createFailed", "Could not create the tag.");
          });
    });
    create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    HorizontalLayout row = new HorizontalLayout(name, create);
    row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
    return row;
  }

  private HorizontalLayout rowActions(TagInfo t) {
    Button assign = new Button(new Icon(VaadinIcon.PLUS), _ -> new AssignTagDialog(t.tag(), this::refresh).open());
    assign.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    assign.getElement().setAttribute("title", tr("tags.assign", "Assign links"));
    // Attaching own links stays a USER action; the registry mutations
    // (rename/replace/delete) are admin-only since V00.17.00 P060.
    if (!CurrentUser.isAdmin()) {
      HorizontalLayout row = new HorizontalLayout(assign);
      row.setSpacing(true);
      return row;
    }
    Button rename = new Button(new Icon(VaadinIcon.EDIT), _ -> openRenameDialog(t.tag()));
    rename.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    rename.getElement().setAttribute("title", tr("tags.rename", "Rename"));
    Button replace = new Button(new Icon(VaadinIcon.EXCHANGE), _ -> openReplaceDialog(t.tag()));
    replace.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    replace.getElement().setAttribute("title", tr("tags.replace", "Replace by 1..n tags"));
    Button delete = new Button(new Icon(VaadinIcon.TRASH), _ -> openDeleteDialog(t.tag()));
    delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    delete.getElement().setAttribute("title", tr("tags.delete", "Delete"));
    HorizontalLayout row = new HorizontalLayout(assign, rename, replace, delete);
    row.setSpacing(true);
    return row;
  }

  private void openReplaceDialog(String from) {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(tr("tags.replace.title", "Replace tag “{0}”", from));
    MultiSelectComboBox<String> targets = new MultiSelectComboBox<>(tr("tags.replace.targets", "Replace with (1..n tags)"));
    targets.setAllowCustomValue(true);
    targets.setWidthFull();
    var items = new java.util.LinkedHashSet<String>();
    targets.setItems(items);
    targets.addCustomValueSetListener(e -> {
      String v = e.getDetail() == null ? "" : e.getDetail().trim().toLowerCase(Locale.ROOT);
      if (v.isEmpty()) return;
      var sel = new java.util.LinkedHashSet<>(targets.getSelectedItems());
      sel.add(v);
      items.add(v);
      targets.setItems(items);
      targets.setValue(sel);
    });
    dlg.add(targets);
    Button apply = new Button(tr("tags.replace.button", "Replace"), _ -> {
      List<String> to = new ArrayList<>(targets.getValue());
      CampaignClientFactory.newInstance().replaceTag(from, to)
          .peek(ok -> {
            Notifications.successKey("tags.replaced", "Tag replaced.");
            dlg.close();
            refresh();
          })
          .peekFailure(err -> {
            logger().info("replaceTag failed: {}", err);
            Notifications.errorKey("tags.replaceFailed", "Could not replace the tag.");
          });
    });
    apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    dlg.getFooter().add(new Button(tr("common.cancel", "Cancel"), _ -> dlg.close()), apply);
    dlg.open();
  }

  private void refresh() {
    linksTitle.setVisible(false);
    linksGrid.setVisible(false);
    CampaignClientFactory.newInstance().listTags()
        .peek(tags -> {
          tagsGrid.setItems(tags);
          boolean empty = tags.isEmpty();
          tagsGrid.setVisible(!empty);
          emptyHint.setVisible(empty);
        })
        .peekFailure(err -> {
          logger().info("tag list failed: {}", err);
          Notifications.errorKey("tags.loadFailed", "Could not load tags.");
        });
  }

  private void showLinksOfTag(TagInfo t) {
    if (t == null) {
      linksTitle.setVisible(false);
      linksGrid.setVisible(false);
      return;
    }
    try {
      URLShortenerClient client = UrlShortenerClientFactory.newInstance();
      List<ShortUrlMapping> links = client.list(UrlMappingListRequest.builder().tag(t.tag()).size(500).build());
      linksGrid.setItems(links);
      linksTitle.setText(tr("tags.links.title", "Links tagged “{0}”", t.tag()));
      linksTitle.setVisible(true);
      linksGrid.setVisible(true);
    } catch (IOException e) {
      logger().info("could not load links of tag {}: {}", t.tag(), e.getMessage());
      Notifications.errorKey("tags.loadFailed", "Could not load tags.");
    }
  }

  private void openRenameDialog(String tag) {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(tr("tags.rename.title", "Rename tag “{0}”", tag));
    TextField newName = new TextField(tr("tags.rename.newName", "New name"));
    newName.setValue(tag);
    newName.setWidthFull();
    dlg.add(newName);
    Button save = new Button(tr("common.save", "Save"), _ -> {
      String to = newName.getValue() == null ? "" : newName.getValue().trim();
      if (to.isBlank()) {
        Notifications.warningKey("tags.rename.newNameRequired", "Enter a new tag name.");
        return;
      }
      CampaignClientFactory.newInstance().renameTag(tag, to)
          .peek(ok -> {
            Notifications.successKey("tags.renamed", "Tag renamed.");
            dlg.close();
            refresh();
          })
          .peekFailure(err -> {
            logger().info("renameTag failed: {}", err);
            Notifications.errorKey("tags.renameFailed", "Could not rename the tag.");
          });
    });
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    Button cancel = new Button(tr("common.cancel", "Cancel"), _ -> dlg.close());
    dlg.getFooter().add(cancel, save);
    dlg.open();
  }

  private void openDeleteDialog(String tag) {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(tr("tags.delete.title", "Delete tag “{0}”?", tag));
    dlg.add(tr("tags.delete.question", "This removes the tag from every link that carries it."));
    Button confirm = new Button(tr("tags.delete", "Delete"), _ ->
        CampaignClientFactory.newInstance().deleteTag(tag)
            .peek(ok -> {
              Notifications.successKey("tags.deleted", "Tag deleted.");
              dlg.close();
              refresh();
            })
            .peekFailure(err -> {
              logger().info("deleteTag failed: {}", err);
              Notifications.errorKey("tags.deleteFailed", "Could not delete the tag.");
            }));
    confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
    Button cancel = new Button(tr("common.cancel", "Cancel"), _ -> dlg.close());
    dlg.getFooter().add(cancel, confirm);
    dlg.open();
  }
}
