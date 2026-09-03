package com.svenruppert.urlshortener.api.store.provider.inmemory;

import com.svenruppert.urlshortener.api.extension.LinkMetaStorage;
import com.svenruppert.urlshortener.core.campaign.Campaign;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Non-persistent link-meta seam backing (in-memory server profile + tests). */
public final class InMemoryLinkMetaStorage
    implements LinkMetaStorage {

  private final Map<String, Campaign> campaigns = new ConcurrentHashMap<>();
  private final Map<String, String> campaignOfLink = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> linkTags = new ConcurrentHashMap<>();
  private final Map<String, String> linkLabels = new ConcurrentHashMap<>();
  private final Map<String, Boolean> knownTags = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> analystCampaigns = new ConcurrentHashMap<>();

  @Override
  public Map<String, Campaign> campaigns() {
    return campaigns;
  }

  @Override
  public Map<String, String> campaignOfLink() {
    return campaignOfLink;
  }

  @Override
  public Map<String, Set<String>> linkTags() {
    return linkTags;
  }

  @Override
  public Map<String, String> linkLabels() {
    return linkLabels;
  }

  @Override
  public Map<String, Boolean> knownTags() {
    return knownTags;
  }

  @Override
  public Map<String, Set<String>> analystCampaigns() {
    return analystCampaigns;
  }

  @Override
  public void persist() {
  }
}
