package com.svenruppert.urlshortener.api.extension;

import com.svenruppert.urlshortener.core.campaign.Campaign;

import java.util.Map;
import java.util.Set;

/**
 * Raw storage access for the link-meta plug-in family (campaigns, tags,
 * labels). The community edition keeps these maps in its persistent root so
 * pre-split data stays loadable and merely dormant (V00.18.00 P085, D4) —
 * but no community code reads or writes them; only plug-ins do, through this
 * seam. Both storage profiles (persistent and in-memory) provide an
 * implementation via {@link ServerContext#linkMetaStorage()}.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public interface LinkMetaStorage {

  Map<String, Campaign> campaigns();

  /** shortCode -&gt; campaignId */
  Map<String, String> campaignOfLink();

  /** shortCode -&gt; tags */
  Map<String, Set<String>> linkTags();

  /** shortCode -&gt; placement label */
  Map<String, String> linkLabels();

  /** Tag registry (keys are the defined tags, including empty ones). */
  Map<String, Boolean> knownTags();

  /** Persists the maps after a mutation; no-op for the in-memory profile. */
  void persist();
}
