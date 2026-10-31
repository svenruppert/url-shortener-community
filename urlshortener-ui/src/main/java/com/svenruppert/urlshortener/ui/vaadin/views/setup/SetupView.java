package com.svenruppert.urlshortener.ui.vaadin.views.setup;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.client.AuthClient;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.svenruppert.urlshortener.ui.vaadin.views.login.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * First-admin setup page (standalone, no layout, public route). Shown
 * on a fresh instance where {@code GET /api/bootstrap/status} reports
 * {@code bootstrapRequired=true}: the operator pastes the one-time
 * bootstrap token (printed to the server console / stored in
 * {@code <app.storage.dir>/jcustos-store/bootstrap.token}) and picks
 * the first admin's credentials, which are created via
 * {@code POST /api/setup}. After success it forwards to the
 * {@link LoginView}. Once an admin exists this route redirects to
 * login, so it cannot be used to create a second "first" admin.
 */
@PageTitle("Setup | URL Shortener")
@Route(SetupView.PATH)
public class SetupView
    extends VerticalLayout
    implements HasLogger, I18nSupport, BeforeEnterObserver {

  public static final String PATH = "setup";

  private static final String K_TITLE = "setup.title";
  private static final String K_INTRO = "setup.intro";
  private static final String K_TOKEN = "setup.token";
  private static final String K_USERNAME = "setup.username";
  private static final String K_PASSWORD = "setup.password";
  private static final String K_REPEAT = "setup.repeatPassword";
  private static final String K_CREATE = "setup.create";
  private static final String K_MISMATCH = "setup.error.mismatch";
  private static final String K_TOO_SHORT = "setup.error.tooShort";
  private static final String K_MISSING = "setup.error.missing";
  private static final String K_CREATED = "setup.created";
  private static final String K_BAD_TOKEN = "setup.error.badToken";
  private static final String K_COMPROMISED = "setup.error.compromised";
  private static final String K_ALREADY = "setup.error.already";
  private static final String K_FAILED = "setup.error.failed";

  private static final int MIN_PASSWORD_LENGTH = 8;

  private final AuthClient authClient = new AuthClient();

  private final TextField token = new TextField();
  private final TextField username = new TextField();
  private final PasswordField password = new PasswordField();
  private final PasswordField repeat = new PasswordField();

  public SetupView() {
    setSpacing(true);
    setPadding(true);
    setMaxWidth("30em");

    // jSentinel brand accent (lime-gold) at the top of the view.
    var brandBar = new com.vaadin.flow.component.html.Div();
    brandBar.addClassName("js-brand-bar");
    add(brandBar);
    add(new H2(tr(K_TITLE, "Initial setup")));
    add(new Paragraph(tr(K_INTRO,
        "No administrator exists yet. Paste the one-time bootstrap token "
            + "from the server console and choose the first admin's credentials.")));

    token.setLabel(tr(K_TOKEN, "Bootstrap token"));
    username.setLabel(tr(K_USERNAME, "Admin username"));
    password.setLabel(tr(K_PASSWORD, "Password"));
    repeat.setLabel(tr(K_REPEAT, "Repeat password"));
    token.setWidthFull();
    username.setWidthFull();
    password.setWidthFull();
    repeat.setWidthFull();

    Button create = new Button(tr(K_CREATE, "Create administrator"), _ -> submit());
    create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    add(token, username, password, repeat, create);
  }

  /** If an admin already exists, this page is pointless — go to login. */
  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    authClient.bootstrapStatus()
        .peek(status -> {
          if (!status.bootstrapRequired()) {
            event.forwardTo(LoginView.class);
          }
        });
  }

  private void submit() {
    String tok = token.getValue();
    String user = username.getValue();
    String pw = password.getValue();
    String pw2 = repeat.getValue();

    if (isBlank(tok) || isBlank(user) || isBlank(pw)) {
      Notifications.warningKey(K_MISSING, "Token, username and password are required.");
      return;
    }
    if (pw.length() < MIN_PASSWORD_LENGTH) {
      Notifications.warningKey(K_TOO_SHORT, "Password must be at least {0} characters.", MIN_PASSWORD_LENGTH);
      return;
    }
    if (!pw.equals(pw2)) {
      Notifications.warningKey(K_MISMATCH, "The passwords do not match.");
      return;
    }

    authClient.setup(tok.trim(), user.trim(), pw)
        .peek(ok -> {
          Notifications.successKey(K_CREATED, "Administrator created. Please sign in.");
          UI.getCurrent().navigate(LoginView.class);
        })
        .peekFailure(this::humanise);
  }

  private void humanise(String error) {
    logger().info("setup failed: {}", error);
    String message = error == null ? "" : error;
    if (message.contains(": 401") || message.contains("invalid bootstrap token")) {
      Notifications.errorKey(K_BAD_TOKEN, "Invalid or expired bootstrap token.");
    } else if (message.contains("compromised")) {
      Notifications.errorKey(K_COMPROMISED, "That password is known to be compromised.");
    } else if (message.contains(": 409") || message.contains("already initialised")) {
      Notifications.errorKey(K_ALREADY, "An administrator already exists.");
      UI.getCurrent().navigate(LoginView.class);
    } else {
      Notifications.errorKey(K_FAILED, "Setup failed. Check the token and try again.");
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
