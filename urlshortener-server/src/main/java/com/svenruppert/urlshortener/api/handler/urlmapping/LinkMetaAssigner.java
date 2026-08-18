package com.svenruppert.urlshortener.api.handler.urlmapping;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.campaign.CampaignStore;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Applies optional create-time campaign metadata (campaign membership, tags,
 * placement label) to a freshly created link. Best-effort: the link already
 * exists and its 201/response is unaffected, so a metadata failure is logged and
 * swallowed rather than surfaced to the caller. Blank/empty inputs are ignored.
 */
final class LinkMetaAssigner {

  private LinkMetaAssigner() {
  }

  static void apply(CampaignStore campaignStore, String shortCode,
                    String campaignId, List<String> tags, String label) {
    if (campaignStore == null || shortCode == null || shortCode.isBlank()) return;
    try {
      if (campaignId != null && !campaignId.isBlank()) {
        campaignStore.assignLink(shortCode, campaignId);
      }
      if (tags != null && !tags.isEmpty()) {
        campaignStore.setTags(shortCode, new LinkedHashSet<>(tags));
      }
      if (label != null && !label.isBlank()) {
        campaignStore.setLabel(shortCode, label);
      }
    } catch (RuntimeException e) {
      HasLogger.staticLogger()
          .warn("create-time metadata assignment failed for '{}': {}", shortCode, e.toString());
    }
  }
}
