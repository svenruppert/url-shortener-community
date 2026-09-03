package com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions;

import com.svenruppert.urlshortener.api.extension.LinkMetaStorage;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.DataRoot;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import org.eclipse.store.storage.types.StorageManager;

import java.util.Map;
import java.util.Set;

/**
 * Persistent backing for the link-meta plug-in seam (V00.18.00 P085): the
 * campaign/tag/label maps live in {@link DataRoot} exactly as before the
 * open-core split, so pre-split data stays loadable — dormant without a
 * plug-in, re-awakened by a licensed one (D4). Community code never touches
 * these maps.
 */
public final class EclipseLinkMetaStorage
    implements LinkMetaStorage {

  private final StorageManager storage;

  public EclipseLinkMetaStorage(StorageManager storage) {
    this.storage = storage;
  }

  private DataRoot dataRoot() {
    return (DataRoot) storage.root();
  }

  @Override
  public Map<String, Campaign> campaigns() {
    return dataRoot().campaigns();
  }

  @Override
  public Map<String, String> campaignOfLink() {
    return dataRoot().campaignOfLink();
  }

  @Override
  public Map<String, Set<String>> linkTags() {
    return dataRoot().linkTags();
  }

  @Override
  public Map<String, String> linkLabels() {
    return dataRoot().linkLabels();
  }

  @Override
  public Map<String, Boolean> knownTags() {
    return dataRoot().knownTags();
  }

  @Override
  public Map<String, Set<String>> analystCampaigns() {
    return dataRoot().analystCampaigns();
  }

  @Override
  public void persist() {
    storage.store(dataRoot().campaigns());
    storage.store(dataRoot().campaignOfLink());
    storage.store(dataRoot().linkTags());
    storage.store(dataRoot().linkLabels());
    storage.store(dataRoot().knownTags());
    storage.store(dataRoot().analystCampaigns());
  }
}
