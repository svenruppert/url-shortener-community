package com.svenruppert.urlshortener.ui.vaadin.views.users;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.UsersClient;
import com.svenruppert.urlshortener.core.auth.UserSummary;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.tools.UsersClientFactory;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

/**
 * Admin user management (PRE-REBUILD-SCOPE Kap. 3.9): a grid of users
 * with create, role assign / revoke and delete actions. Talks to the
 * server through {@link UsersClient} (session Bearer token attached by
 * {@link UsersClientFactory}). Server errors are humanised.
 */
@PageTitle("Users")
@Route(value = UserManagementView.PATH, layout = MainLayout.class)
@VisibleFor(AppRole.ADMIN)
public class UserManagementView
    extends VerticalLayout
    implements HasLogger, I18nSupport {

  public static final String PATH = "users";

  private static final String K_TITLE = "users.title";
  private static final String K_NEW = "users.new";
  private static final String K_REFRESH = "users.refresh";
  private static final String K_COL_ID = "users.col.id";
  private static final String K_COL_NAME = "users.col.name";
  private static final String K_COL_ROLES = "users.col.roles";
  private static final String K_COL_ACTIONS = "users.col.actions";
  private static final String K_MAKE_ADMIN = "users.makeAdmin";
  private static final String K_DROP_ADMIN = "users.dropAdmin";
  private static final String K_DELETE = "users.delete";
  private static final String K_LOAD_FAILED = "users.loadFailed";
  private static final String K_CREATED = "users.created";
  private static final String K_DELETED = "users.deleted";
  private static final String K_ROLE_UPDATED = "users.roleUpdated";

  private static final int MIN_PASSWORD_LENGTH = 8;

  private final UsersClient usersClient = UsersClientFactory.newInstance();
  private final Grid<UserSummary> grid = new Grid<>(UserSummary.class, false);

  public UserManagementView() {
    setSizeFull();
    setPadding(true);

    // jSentinel brand accent (lime-gold) at the top of the view.
    var brandBar = new com.vaadin.flow.component.html.Div();
    brandBar.addClassName("js-brand-bar");
    add(brandBar);
    add(new H2(tr(K_TITLE, "User management")));

    Button newUser = new Button(tr(K_NEW, "New user"), _ -> openCreateDialog());
    newUser.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    Button refresh = new Button(tr(K_REFRESH, "Refresh"), _ -> reload());
    add(new HorizontalLayout(newUser, refresh));

    grid.addColumn(UserSummary::id).setHeader(tr(K_COL_ID, "Id")).setAutoWidth(true);
    grid.addColumn(UserSummary::name).setHeader(tr(K_COL_NAME, "Name")).setAutoWidth(true);
    grid.addColumn(u -> String.join(", ", u.roles()))
        .setHeader(tr(K_COL_ROLES, "Roles")).setFlexGrow(1);
    grid.addComponentColumn(this::rowActions)
        .setHeader(tr(K_COL_ACTIONS, "Actions")).setAutoWidth(true);
    grid.setSizeFull();
    add(grid);

    reload();
  }

  private HorizontalLayout rowActions(UserSummary user) {
    boolean isAdmin = user.roles().contains(AppRole.ADMIN.name());
    Button roleToggle = isAdmin
        ? new Button(tr(K_DROP_ADMIN, "Revoke admin"), _ -> revokeAdmin(user))
        : new Button(tr(K_MAKE_ADMIN, "Make admin"), _ -> assignAdmin(user));
    Button delete = new Button(tr(K_DELETE, "Delete"), _ -> deleteUser(user));
    delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    HorizontalLayout actions = new HorizontalLayout(roleToggle, delete);
    actions.setSpacing(true);
    return actions;
  }

  private void reload() {
    usersClient.list()
        .peek(grid::setItems)
        .peekFailure(error -> {
          logger().info("user list failed: {}", error);
          Notifications.errorKey(K_LOAD_FAILED, "Could not load users.");
        });
  }

  private void assignAdmin(UserSummary user) {
    usersClient.assignRole(user.id(), AppRole.ADMIN.name())
        .peek(u -> {
          Notifications.successKey(K_ROLE_UPDATED, "Roles updated.");
          reload();
        })
        .peekFailure(error -> humanise(error));
  }

  private void revokeAdmin(UserSummary user) {
    usersClient.revokeRole(user.id(), AppRole.ADMIN.name())
        .peek(u -> {
          Notifications.successKey(K_ROLE_UPDATED, "Roles updated.");
          reload();
        })
        .peekFailure(error -> humanise(error));
  }

  private void deleteUser(UserSummary user) {
    usersClient.delete(user.id())
        .peek(ok -> {
          Notifications.successKey(K_DELETED, "User deleted.");
          reload();
        })
        .peekFailure(error -> humanise(error));
  }

  private void openCreateDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(tr(K_NEW, "New user"));

    TextField username = new TextField(tr(K_COL_NAME, "Name"));
    PasswordField password = new PasswordField(tr("users.password", "Password"));
    username.setWidthFull();
    password.setWidthFull();

    Button create = new Button(tr("users.create", "Create"), _ -> {
      if (username.isEmpty() || password.getValue().length() < MIN_PASSWORD_LENGTH) {
        Notifications.warningKey("users.invalidInput",
            "A name and a password of at least {0} characters are required.", MIN_PASSWORD_LENGTH);
        return;
      }
      usersClient.create(username.getValue(), password.getValue(), List.of(AppRole.USER.name()))
          .peek(u -> {
            Notifications.successKey(K_CREATED, "User created.");
            dialog.close();
            reload();
          })
          .peekFailure(error -> humanise(error));
    });
    create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    Button cancel = new Button(tr("users.cancel", "Cancel"), _ -> dialog.close());

    dialog.add(new VerticalLayout(username, password));
    dialog.getFooter().add(cancel, create);
    dialog.open();
  }

  /** Maps stable server error codes to user-facing messages. */
  private void humanise(String error) {
    logger().info("users operation failed: {}", error);
    String message = error == null ? "" : error;
    if (message.contains("last_admin_protected")) {
      Notifications.errorKey("users.error.lastAdmin", "The last administrator cannot be removed.");
    } else if (message.contains("username_exists")) {
      Notifications.errorKey("users.error.exists", "That username already exists.");
    } else if (message.contains("unknown_role")) {
      Notifications.errorKey("users.error.role", "Unknown role.");
    } else if (message.contains("compromised")) {
      Notifications.errorKey("users.error.compromised", "That password is known to be compromised.");
    } else {
      Notifications.errorKey("users.error.generic", "The operation failed.");
    }
  }
}
