package com.svenruppert.urlshortener.api.extension;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hook a plug-in registers (via {@link LinkLifecycleContribution}) to observe
 * and extend the community link lifecycle without any community source knowing
 * the feature: create-time metadata riding along in the request JSON, delete
 * cascades, and list-filter restrictions.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface LinkLifecycleListener {

  /**
   * Called after a link was created. {@code rawRequestJson} is the original
   * request body — plug-in-owned fields (unknown to the community DTOs, which
   * ignore them) are parsed here. {@code bulk} distinguishes the bulk-create
   * JSON shape from the single-create shape.
   */
  default void onLinkCreated(String shortCode, String rawRequestJson, boolean bulk) {
  }

  /** Cascade when a link is deleted. */
  default void onLinkDeleted(String shortCode) {
  }

  /**
   * Resolves plug-in query parameters of the list endpoints (e.g. a tag
   * filter) into a short-code restriction; {@code null} means no restriction
   * contributed.
   */
  default Set<String> listRestriction(Map<String, List<String>> query) {
    return null;
  }
}
