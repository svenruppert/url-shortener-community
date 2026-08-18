package com.svenruppert.urlshortener.api.store.provider.inmemory;

import com.svenruppert.urlshortener.api.store.campaign.AbstractCampaignStore;
import com.svenruppert.urlshortener.core.campaign.Campaign;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Non-persistent campaign store (used in the non-persistent server profile + tests). */
public final class InMemoryCampaignStore
    extends AbstractCampaignStore {

  private final Map<String, Campaign> campaigns = new ConcurrentHashMap<>();
  private final Map<String, String> campaignOfLink = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> linkTags = new ConcurrentHashMap<>();
  private final Map<String, String> linkLabels = new ConcurrentHashMap<>();
  private final Map<String, Boolean> knownTags = new ConcurrentHashMap<>();

  @Override
  protected Map<String, Campaign> campaignsMap() {
    return campaigns;
  }

  @Override
  protected Map<String, String> campaignOfLinkMap() {
    return campaignOfLink;
  }

  @Override
  protected Map<String, Set<String>> linkTagsMap() {
    return linkTags;
  }

  @Override
  protected Map<String, String> linkLabelsMap() {
    return linkLabels;
  }

  @Override
  protected Map<String, Boolean> knownTagsMap() {
    return knownTags;
  }
}
