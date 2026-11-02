package com.svenruppert.urlshortener.api.extension;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry for {@link LinkTargetGuard}s discovered via {@link ServiceLoader}.
 * Consulted on the create path ({@code MappingCreator}) and the redirect path
 * ({@code RedirectHandler}). With no guard on the classpath (community-only)
 * {@link #firstVeto} always allows, so there is no behavior change.
 *
 * @since 00.23.00
 */
public final class LinkTargetGuards
    implements HasLogger {

  private static volatile List<LinkTargetGuard> guards;

  private LinkTargetGuards() {
  }

  private static List<LinkTargetGuard> guards() {
    List<LinkTargetGuard> local = guards;
    if (local != null) return local;
    synchronized (LinkTargetGuards.class) {
      if (guards == null) {
        List<LinkTargetGuard> found = new ArrayList<>();
        for (LinkTargetGuard g : ServiceLoader.load(
            LinkTargetGuard.class, Thread.currentThread().getContextClassLoader())) {
          found.add(g);
        }
        guards = List.copyOf(found);
      }
      return guards;
    }
  }

  /**
   * @return the first guard's veto reason for {@code targetUrl}, or empty when
   *         every guard allows it. A guard that throws is treated as "allow".
   */
  public static Optional<String> firstVeto(String targetUrl) {
    if (targetUrl == null || targetUrl.isBlank()) return Optional.empty();
    for (LinkTargetGuard guard : guards()) {
      try {
        Optional<String> reason = guard.veto(targetUrl);
        if (reason != null && reason.isPresent()) return reason;
      } catch (RuntimeException guardFailed) {
        HasLogger.staticLogger().warn("link-target guard {} failed, allowing: {}",
            guard.getClass().getName(), guardFailed.toString());
      }
    }
    return Optional.empty();
  }

  /** Test seam: drop the cached guards so the next call re-discovers them. */
  public static void reload() {
    synchronized (LinkTargetGuards.class) {
      guards = null;
    }
  }
}
