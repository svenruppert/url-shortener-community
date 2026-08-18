package com.svenruppert.urlshortener.api.extension;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Discovers {@link HandlerContribution} plug-ins via {@link ServiceLoader}
 * and validates them before registration: a duplicate path between plug-ins
 * aborts server startup ({@link IllegalStateException}) — silently shadowed
 * endpoints would be a security hazard. Collisions with community routes fail
 * fast too, via {@code HttpServer.createContext} rejecting duplicate
 * contexts.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public final class HandlerRegistry {

  private HandlerRegistry() {
  }

  /** Loads all contributions and fails fast on duplicate plug-in paths. */
  public static List<HandlerContribution> discover() {
    List<HandlerContribution> found = new ArrayList<>();
    ServiceLoader.load(HandlerContribution.class).forEach(found::add);
    validate(found);
    found.forEach(c -> HasLogger.staticLogger()
        .info("server handler contribution discovered: {} ({})", c.path(), c.getClass().getName()));
    return List.copyOf(found);
  }

  public static void validate(List<HandlerContribution> contributions) {
    Set<String> seen = new HashSet<>();
    for (HandlerContribution contribution : contributions) {
      String path = contribution.path();
      if (path == null || path.isBlank() || !path.startsWith("/")) {
        throw new IllegalStateException(
            "handler contribution " + contribution.getClass().getName()
                + " declares an invalid path: '" + path + "'");
      }
      if (!seen.add(path)) {
        throw new IllegalStateException(
            "duplicate handler-contribution path '" + path + "' — refusing to start");
      }
    }
  }
}
