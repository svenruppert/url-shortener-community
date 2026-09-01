package com.svenruppert.urlshortener.api.security.bootstrap;

import eu.jsentinel.jcustos.dx.rest.bootstrap.RestJCustosBootstrap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Aggregates every registered {@link BootstrapExtension} via
 * {@link ServiceLoader}, sorts by {@link BootstrapExtension#order()},
 * and applies them in a single {@code .audit(...)} /
 * {@code .sessions(...)} / {@code .credentials(...)} call on the
 * fluent {@code RestSecurity.bootstrap()} chain. Later stages add a
 * new extension; the entry point in {@code ShortenerServer} never
 * has to change.
 */
public final class BootstrapBuilder {

  private BootstrapBuilder() {
  }

  public static RestJCustosBootstrap apply(RestJCustosBootstrap builder) {
    List<BootstrapExtension> extensions = new ArrayList<>();
    ServiceLoader.load(BootstrapExtension.class).forEach(extensions::add);
    extensions.sort(Comparator.comparingInt(BootstrapExtension::order));
    return builder
        .audit(a -> extensions.forEach(e -> e.contributeAudit(a)))
        .sessions(s -> extensions.forEach(e -> e.contributeSessions(s)))
        .credentials(c -> extensions.forEach(e -> e.contributeCredentials(c)));
  }
}
