package com.svenruppert.urlshortener.core.campaign;

import java.util.List;
import java.util.Map;

/**
 * A portable snapshot of all campaign metadata for a lossless export/import
 * round-trip: the campaign definitions, per-link campaign membership, per-link
 * tags (m2m) and per-link placement labels.
 *
 * @param formatVersion  export format version
 * @param campaigns      campaign definitions
 * @param campaignOfLink shortCode → campaignId (1:many membership)
 * @param linkTags       shortCode → tags (normalized on import)
 * @param linkLabels     shortCode → placement label
 * @param knownTags      the tag registry (all defined tags, including empty ones); may be null in older exports
 */
public record CampaignMetaExport(String formatVersion,
                                 List<Campaign> campaigns,
                                 Map<String, String> campaignOfLink,
                                 Map<String, List<String>> linkTags,
                                 Map<String, String> linkLabels,
                                 List<String> knownTags) {

  public static final String FORMAT_VERSION = "2";
}
