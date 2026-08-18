package com.svenruppert.urlshortener.ui.vaadin.security.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authentication.AuthenticationService;
import com.svenruppert.jsentinel.authorization.api.AuthorizationService;
import com.svenruppert.jsentinel.dx.runtime.JSentinelRuntime;
import com.svenruppert.jsentinel.dx.vaadin.bootstrap.VaadinSecurity;
import com.svenruppert.jsentinel.starter.profile.VaadinJSentinelStarter;
import com.svenruppert.urlshortener.ui.vaadin.views.login.LoginView;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the fluent {@link VaadinSecurity#bootstrap()} chain once per
 * JVM at Vaadin service init.
 *
 * <p>This listener is layer-1 — it stays untouched when persistence,
 * hardening or any other layer is applied later. Sub-aspect
 * configuration (audit, sessions, credentials) flows through
 * {@link BootstrapBuilder#apply(com.svenruppert.jsentinel.dx.vaadin.bootstrap.VaadinJSentinelBootstrap)},
 * which loads every registered {@link BootstrapExtension} via
 * {@link ServiceLoader}. New layers add a new extension; the entry
 * point here never has to change.
 */
public class JSentinelBootstrapInitListener
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
      // request silently bypasses jSentinel, which is the worst possible
      // failure mode for a security stack. Log the diagnosis and abort
      // Vaadin init so the deployment surfaces the broken state
      // immediately instead of silently rendering a wide-open app.
      String diagnosis = String.format(
          "jSentinel bootstrap requires both AuthenticationService and "
              + "AuthorizationService via SPI. Found: "
              + "AuthenticationService=%s, AuthorizationService=%s. "
              + "Check the @JSentinelAutoService annotation processor is "
              + "wired in maven-compiler-plugin's annotationProcessorPaths, "
              + "and that target/classes/META-INF/services/com.svenruppert."
              + "jsentinel.{authentication.AuthenticationService,"
              + "authorization.api.AuthorizationService} exist.",
          authn == null ? "<missing>" : authn.getClass().getName(),
          authz == null ? "<missing>" : authz.getClass().getName());
      throw new IllegalStateException(diagnosis);
    }
    JSentinelRuntime runtime = BootstrapBuilder.apply(
        VaadinSecurity.bootstrap()
            .use(switch (com.svenruppert.urlshortener.core.SecurityProfile.current()) {
              case PRODUCTION -> VaadinJSentinelStarter.productionDefaults();
              case STRICT -> VaadinJSentinelStarter.strictDefaults();
              case DEVELOPMENT -> VaadinJSentinelStarter.developmentDefaults();
            })
            .authentication(authn)
            .authorization(authz)
            .loginRoute(LoginView.PATH)
            .stepUpRoute("step-up")
    ).install();
    logger().info("{}", runtime.log());
  }
}
