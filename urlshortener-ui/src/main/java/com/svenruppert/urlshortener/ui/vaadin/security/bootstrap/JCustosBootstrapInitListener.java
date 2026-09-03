package com.svenruppert.urlshortener.ui.vaadin.security.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authentication.AuthenticationService;
import eu.jsentinel.jcustos.authorization.api.AuthorizationService;
import eu.jsentinel.jcustos.dx.runtime.JCustosRuntime;
import eu.jsentinel.jcustos.dx.vaadin.bootstrap.VaadinSecurity;
import eu.jsentinel.jcustos.starter.profile.VaadinJCustosStarter;
import com.svenruppert.urlshortener.ui.vaadin.views.login.LoginView;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import com.svenruppert.urlshortener.core.SecurityProfile;

/**
 * Runs the fluent {@link VaadinSecurity#bootstrap()} chain once per
 * JVM at Vaadin service init.
 *
 * <p>This listener is layer-1 — it stays untouched when persistence,
 * hardening or any other layer is applied later. Sub-aspect
 * configuration (audit, sessions, credentials) flows through
 * {@link BootstrapBuilder#apply(eu.jsentinel.jcustos.dx.vaadin.bootstrap.VaadinJCustosBootstrap)},
 * which loads every registered {@link BootstrapExtension} via
 * {@link ServiceLoader}. New layers add a new extension; the entry
 * point here never has to change.
 */
public class JCustosBootstrapInitListener
    implements VaadinServiceInitListener, HasLogger {

  private static final AtomicBoolean DONE = new AtomicBoolean();

  @Override
  public void serviceInit(ServiceInitEvent event) {
    if (!DONE.compareAndSet(false, true)) {
      return;
    }
    AuthenticationService<?, ?> authn = ServiceLoader.load(AuthenticationService.class)
        .findFirst().orElse(null);
    AuthorizationService<?> authz = ServiceLoader.load(AuthorizationService.class)
        .findFirst().orElse(null);
    if (authn == null || authz == null) {
      // Fail fast — running an app with one of these missing means every
      // request silently bypasses jCustos, which is the worst possible
      // failure mode for a security stack. Log the diagnosis and abort
      // Vaadin init so the deployment surfaces the broken state
      // immediately instead of silently rendering a wide-open app.
      String diagnosis = String.format(
          "jCustos bootstrap requires both AuthenticationService and "
              + "AuthorizationService via SPI. Found: "
              + "AuthenticationService=%s, AuthorizationService=%s. "
              + "Check the @JCustosAutoService annotation processor is "
              + "wired in maven-compiler-plugin's annotationProcessorPaths, "
              + "and that target/classes/META-INF/services/com.svenruppert."
              + "jsentinel.{authentication.AuthenticationService,"
              + "authorization.api.AuthorizationService} exist.",
          authn == null ? "<missing>" : authn.getClass().getName(),
          authz == null ? "<missing>" : authz.getClass().getName());
      throw new IllegalStateException(diagnosis);
    }
    JCustosRuntime runtime = BootstrapBuilder.apply(
        VaadinSecurity.bootstrap()
            .use(switch (SecurityProfile.current()) {
              case PRODUCTION -> VaadinJCustosStarter.productionDefaults();
              case STRICT -> VaadinJCustosStarter.strictDefaults();
              case DEVELOPMENT -> VaadinJCustosStarter.developmentDefaults();
            })
            .authentication(authn)
            .authorization(authz)
            .loginRoute(LoginView.PATH)
            .stepUpRoute("step-up")
    ).install();
    logger().info("{}", runtime.log());
  }
}
