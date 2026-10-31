package com.svenruppert.urlshortener.ui.vaadin.views.profile;

import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.api.SubjectStores;
import com.svenruppert.urlshortener.client.ApiKeysClient;
import com.svenruppert.urlshortener.client.AuthClient;
import com.svenruppert.urlshortener.core.auth.ApiKeySummary;
import com.svenruppert.urlshortener.ui.vaadin.MainLayout;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;
import com.svenruppert.urlshortener.ui.vaadin.security.VisibleFor;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Self-service profile view (PRE-REBUILD-SCOPE Kap. 3.11): shows the
 * logged-in user's name + roles and lets them change their own
 * password via {@code POST /api/me/password}. The server + client
 * data layer (Stage G) is already in place.
 */
@PageTitle("Profile")
@Route(value = ProfileView.PATH, layout = MainLayout.class)
@VisibleFor(AppRole.USER)
public class ProfileView
    extends VerticalLayout
    implements HasLogger, I18nSupport {

  public static final String PATH = "profile";

  private static final String K_TITLE = "profile.title";
  private static final String K_USERNAME = "profile.username";
  private static final String K_ROLES = "profile.roles";
  private static final String K_CHANGE_PW = "profile.changePassword";
  private static final String K_CURRENT_PW = "profile.currentPassword";
  private static final String K_NEW_PW = "profile.newPassword";
  private static final String K_REPEAT_PW = "profile.repeatPassword";
  private static final String K_SAVE = "profile.save";
  private static final String K_PW_MISMATCH = "profile.error.mismatch";
  private static final String K_PW_TOO_SHORT = "profile.error.tooShort";
  private static final String K_PW_CHANGED = "profile.passwordChanged";
  private static final String K_PW_FAILED = "profile.passwordChangeFailed";

  private static final String K_AK_TITLE = "profile.apiKeys.title";
  private static final String K_AK_LABEL = "profile.apiKeys.label";
  private static final String K_AK_CREATED = "profile.apiKeys.created";
  private static final String K_AK_NEW_LABEL = "profile.apiKeys.newLabel";
  private static final String K_AK_CREATE = "profile.apiKeys.create";
  private static final String K_AK_REVOKE = "profile.apiKeys.revoke";
  private static final String K_AK_REVOKE_CONFIRM = "profile.apiKeys.revokeConfirm";
  private static final String K_AK_REVOKED = "profile.apiKeys.revoked";
  private static final String K_AK_REVOKE_FAILED = "profile.apiKeys.revokeFailed";
  private static final String K_AK_CREATE_FAILED = "profile.apiKeys.createFailed";
  private static final String K_AK_NEW_TITLE = "profile.apiKeys.newTitle";
  private static final String K_AK_NEW_WARNING = "profile.apiKeys.newWarning";
  private static final String K_AK_COPY = "profile.apiKeys.copy";
  private static final String K_AK_COPIED = "profile.apiKeys.copied";
  private static final String K_COMMON_CLOSE = "common.close";
  private static final String K_COMMON_CANCEL = "common.cancel";

  private static final int MIN_PASSWORD_LENGTH = 8;

  private final Grid<ApiKeySummary> keysGrid = new Grid<>(ApiKeySummary.class, false);

  public ProfileView() {
    setSpacing(true);
    setPadding(true);

    // jSentinel brand accent (lime-gold) at the top of the view.
    var brandBar = new com.vaadin.flow.component.html.Div();
    brandBar.addClassName("js-brand-bar");
    add(brandBar);
    add(new H2(tr(K_TITLE, "My Profile")));

    Optional<AppUser> current = SubjectStores.subjectStore().currentSubject(AppUser.class);
    String username = current.map(AppUser::name).orElse("—");
    String roles = current
        .map(u -> u.roles().stream().map(Enum::name).sorted().collect(Collectors.joining(", ")))
        .orElse("—");

    add(new Span(tr(K_USERNAME, "Username") + ": " + username));
    add(new Span(tr(K_ROLES, "Roles") + ": " + roles));

    add(new H2(tr(K_CHANGE_PW, "Change password")));

    PasswordField currentPw = new PasswordField(tr(K_CURRENT_PW, "Current password"));
    PasswordField newPw = new PasswordField(tr(K_NEW_PW, "New password"));
    PasswordField repeatPw = new PasswordField(tr(K_REPEAT_PW, "Repeat new password"));
    currentPw.setWidth("22em");
    newPw.setWidth("22em");
    repeatPw.setWidth("22em");

    Button save = new Button(tr(K_SAVE, "Save"), _ ->
        changePassword(currentPw, newPw, repeatPw));
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    add(currentPw, newPw, repeatPw, save);

    buildApiKeysSection();
  }

  private void buildApiKeysSection() {
    add(new H2(tr(K_AK_TITLE, "API keys")));

    keysGrid.addColumn(ApiKeySummary::label).setHeader(tr(K_AK_LABEL, "Label"));
    keysGrid.addColumn(ApiKeySummary::createdAt).setHeader(tr(K_AK_CREATED, "Created"));
    keysGrid.addComponentColumn(this::revokeButton).setHeader("");
    keysGrid.setAllRowsVisible(true);
    keysGrid.setWidth("42em");

    TextField newLabel = new TextField(tr(K_AK_NEW_LABEL, "Label (optional)"));
    newLabel.setWidth("22em");
    Button create = new Button(tr(K_AK_CREATE, "Create key"), _ -> createKey(newLabel));
    create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    HorizontalLayout createRow = new HorizontalLayout(newLabel, create);
    createRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

    add(keysGrid, createRow);
    refreshKeys();
  }

  private ApiKeysClient apiKeys() {
    return new ApiKeysClient().withBearerToken(SessionAuth::currentToken);
  }

  private void refreshKeys() {
    apiKeys().list()
        .peek(keysGrid::setItems)
        .peekFailure(error -> logger().info("api key list failed: {}", error));
  }

  private Button revokeButton(ApiKeySummary key) {
    Button revoke = new Button(tr(K_AK_REVOKE, "Revoke"), _ -> confirmRevoke(key));
    revoke.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    return revoke;
  }

  private void createKey(TextField newLabel) {
    String label = newLabel.getValue() == null ? "" : newLabel.getValue().trim();
    apiKeys().create(label)
        .peek(created -> {
          newLabel.clear();
          refreshKeys();
          showCreatedDialog(created.key());
        })
        .peekFailure(error -> {
          logger().info("api key create rejected: {}", error);
          Notifications.errorKey(K_AK_CREATE_FAILED, "Could not create the API key.");
        });
  }

  private void confirmRevoke(ApiKeySummary key) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(tr(K_AK_REVOKE, "Revoke"));
    dialog.add(new Span(tr(K_AK_REVOKE_CONFIRM,
        "Revoke this API key? Clients using it will stop working immediately.")));
    Button confirm = new Button(tr(K_AK_REVOKE, "Revoke"), _ -> {
      dialog.close();
      apiKeys().revoke(key.id())
          .peek(ok -> {
            Notifications.successKey(K_AK_REVOKED, "API key revoked.");
            refreshKeys();
          })
          .peekFailure(error -> {
            logger().info("api key revoke rejected: {}", error);
            Notifications.errorKey(K_AK_REVOKE_FAILED, "Could not revoke the API key.");
          });
    });
    confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
    Button cancel = new Button(tr(K_COMMON_CANCEL, "Cancel"), _ -> dialog.close());
    dialog.getFooter().add(cancel, confirm);
    dialog.open();
  }

  private void showCreatedDialog(String key) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(tr(K_AK_NEW_TITLE, "Your new API key"));

    Span warning = new Span(tr(K_AK_NEW_WARNING, "Copy it now — it is shown only once."));
    TextField keyField = new TextField();
    keyField.setValue(key);
    keyField.setReadOnly(true);
    keyField.setWidthFull();

    Button copy = new Button(tr(K_AK_COPY, "Copy"), _ -> {
      keyField.getElement().executeJs(
          "navigator.clipboard && navigator.clipboard.writeText($0)", key);
      Notifications.successKey(K_AK_COPIED, "Copied to clipboard.");
    });

    VerticalLayout content = new VerticalLayout(warning, keyField, copy);
    content.setSpacing(true);
    content.setPadding(false);
    dialog.add(content);

    Button close = new Button(tr(K_COMMON_CLOSE, "Close"), _ -> dialog.close());
    dialog.getFooter().add(close);
    dialog.open();
  }

  private void changePassword(PasswordField currentPw, PasswordField newPw, PasswordField repeatPw) {
    String current = currentPw.getValue();
    String next = newPw.getValue();
    String repeat = repeatPw.getValue();

    if (next == null || next.length() < MIN_PASSWORD_LENGTH) {
      Notifications.warningKey(K_PW_TOO_SHORT,
          "New password must be at least {0} characters.", MIN_PASSWORD_LENGTH);
      return;
    }
    if (!next.equals(repeat)) {
      Notifications.warningKey(K_PW_MISMATCH, "The new passwords do not match.");
      return;
    }

    String token = SessionAuth.currentToken();
    var result = new AuthClient().changePassword(token, current, next);
    result
        .peek(ok -> {
          Notifications.successKey(K_PW_CHANGED, "Password changed.");
          currentPw.clear();
          newPw.clear();
          repeatPw.clear();
        })
        .peekFailure(error -> {
          logger().info("password change rejected: {}", error);
          Notifications.errorKey(K_PW_FAILED, "Could not change the password.");
        });
  }
}
