package com.svenruppert.urlshortener.api.store.provider.eclipsestore.patitions;

import com.svenruppert.urlshortener.api.store.campaign.AbstractCampaignStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.DataRoot;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import org.eclipse.store.storage.types.StorageManager;

import java.util.Map;
import java.util.Set;

/**
 * EclipseStore-backed campaign store: campaigns, memberships, tags and labels
 * live in {@link DataRoot} and are persisted on every mutation, so they survive
 * a server restart.
 */
public final class EclipseCampaignStore
    extends AbstractCampaignStore {

  private final StorageManager storage;

  public EclipseCampaignStore(StorageManager storage) {
    this.storage = storage;
  }

  private DataRoot dataRoot() {
    return (DataRoot) storage.root();
  }

  @Override
  protected Map<String, Campaign> campaignsMap() {
    return dataRoot().campaigns();
  }

  @Override
  protected Map<String, String> campaignOfLinkMap() {
    return dataRoot().campaignOfLink();
  }

  @Override
  protected Map<String, Set<String>> linkTagsMap() {
    return dataRoot().linkTags();
  }

  @Override
  protected Map<String, String> linkLabelsMap() {
    return dataRoot().linkLabels();
  }

  @Override
  protected Map<String, Boolean> knownTagsMap() {
    return dataRoot().knownTags();
  }

  @Override
  protected void persist() {
    storage.store(dataRoot().campaigns());
    storage.store(dataRoot().campaignOfLink());
    storage.store(dataRoot().linkTags());
    storage.store(dataRoot().linkLabels());
    storage.store(dataRoot().knownTags());
  }
}
