package com.svenruppert.urlshortener.api.extension;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Aggregates the discovered {@link LinkLifecycleListener}s behind one facade
 * the community handlers call. A plug-in-free classpath yields an empty hook
 * set and zero behavior change; a listener failure is logged and isolated so
 * a plug-in can never break the community request path.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public final class LinkLifecycleHooks
    implements HasLogger {

  private final List<LinkLifecycleListener> listeners;

  private LinkLifecycleHooks(List<LinkLifecycleListener> listeners) {
    this.listeners = listeners;
  }

  public static LinkLifecycleHooks discover(ServerContext context) {
    List<LinkLifecycleListener> found = new ArrayList<>();
    for (LinkLifecycleContribution contribution : ServiceLoader.load(LinkLifecycleContribution.class)) {
      found.add(contribution.create(context));
      HasLogger.staticLogger().info("link lifecycle listener contributed by {}",
          contribution.getClass().getName());
    }
    return new LinkLifecycleHooks(List.copyOf(found));
  }

  public void linkCreated(String shortCode, String rawRequestJson, boolean bulk) {
    for (LinkLifecycleListener listener : listeners) {
      try {
        listener.onLinkCreated(shortCode, rawRequestJson, bulk);
      } catch (RuntimeException e) {
        logger().warn("link-created hook failed for '{}': {}", shortCode, e.toString());
      }
    }
  }

  public void linkDeleted(String shortCode) {
    for (LinkLifecycleListener listener : listeners) {
      try {
        listener.onLinkDeleted(shortCode);
      } catch (RuntimeException e) {
        logger().warn("link-deleted hook failed for '{}': {}", shortCode, e.toString());
      }
    }
  }

  /** Intersection of all contributed restrictions; {@code null} = unrestricted. */
  public Set<String> listRestriction(Map<String, List<String>> query) {
    Set<String> combined = null;
    for (LinkLifecycleListener listener : listeners) {
      Set<String> restriction;
      try {
        restriction = listener.listRestriction(query);
      } catch (RuntimeException e) {
        logger().warn("list-restriction hook failed: {}", e.toString());
        continue;
      }
      if (restriction == null) continue;
      if (combined == null) {
        combined = new LinkedHashSet<>(restriction);
      } else {
        combined.retainAll(restriction);
      }
    }
    return combined;
  }
}
