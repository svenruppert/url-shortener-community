package com.svenruppert.urlshortener.ui.vaadin.views.login;



import eu.jsentinel.jcustos.authorization.api.SubjectStores;
import com.svenruppert.urlshortener.client.AuthClient;
import com.svenruppert.urlshortener.core.auth.MeResponse;
import com.svenruppert.urlshortener.ui.vaadin.security.AppRole;
import com.svenruppert.urlshortener.ui.vaadin.security.AppUser;
import com.svenruppert.urlshortener.ui.vaadin.security.SessionAuth;
import com.svenruppert.urlshortener.ui.vaadin.tools.I18nSupport;
import com.svenruppert.urlshortener.ui.vaadin.views.Notifications;
import com.svenruppert.urlshortener.ui.vaadin.views.overview.OverviewView;
import com.svenruppert.urlshortener.ui.vaadin.views.setup.SetupView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Login form that delegates authentication to the REST backend
 * (Stage D): {@code POST /api/login} yields the Bearer token, stored
 * in the Vaadin session; {@code GET /api/me} yields roles, mapped onto
 * the local {@link AppUser} subject that drives {@code @VisibleFor}
 * navigation gating.
 */
@Route(LoginView.PATH)
public class LoginView
    extends eu.jsentinel.jcustos.authorization.LoginView
    implements I18nSupport, HasDynamicTitle, BeforeEnterObserver {

  public static final String PATH = "login";

  private static final String K_PAGE_TITLE = "login.pageTitle";
  private static final String K_ERR_INCORRECT_PW = "login.error.incorrectPassword";

  private final AuthClient authClient = new AuthClient();

  /**
   * On a fresh instance no admin exists yet — send the operator to the
   * {@link SetupView} instead of a login form they could never pass.
   * Fail-safe: if the status call fails (server down), stay on login.
   */
  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    authClient.bootstrapStatus()
        .peek(status -> {
          if (status.bootstrapRequired()) {
            event.forwardTo(SetupView.class);
          }
        });
  }

  @Override
  public boolean checkCredentials() {
    var loginResult = authClient.login(username(), password());
    if (loginResult.isFailure()) {
      logger().info("REST login rejected for '{}': {}", username(), loginResult);
      return false;
    }

    String token = loginResult.getOrThrow().token();
    SessionAuth.setToken(token);

    // Load roles from the server and mirror them onto the local subject
    // so @VisibleFor gating works without a round-trip per navigation.
    var meResult = authClient.me(token);
    if (meResult.isFailure()) {
      logger().warn("could not load /api/me after login: {}", meResult);
      SessionAuth.clear();
      return false;
    }

    AppUser user = toAppUser(username(), meResult.getOrThrow());
    SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class);
    return true;
  }

  private static AppUser toAppUser(String username, MeResponse me) {
    Set<AppRole> roles = me.roles().stream()
        .map(LoginView::toAppRole)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(AppRole.class)));
    if (roles.isEmpty()) {
      roles = EnumSet.of(AppRole.USER);
    }
    String displayName = me.displayName() != null ? me.displayName() : username;
    return new AppUser(displayName, roles);
  }

  private static AppRole toAppRole(String serverRole) {
    try {
      return AppRole.valueOf(serverRole);
    } catch (IllegalArgumentException unknown) {
      return null;
    }
  }

  @Override
  public void navigateToApp() {
    UI.getCurrent().navigate(OverviewView.class);
  }

  @Override
  public void reactOnFailedLogin() {
    Notifications.errorKey(K_ERR_INCORRECT_PW, "Incorrect password");
  }

  @Override
  public String getPageTitle() {
    return tr(K_PAGE_TITLE, "Admin Login | URL Shortener");
  }
}