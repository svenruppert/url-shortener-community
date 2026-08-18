package com.svenruppert.urlshortener.api.store.provider.eclipsestore;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.security.ApiKeyEntry;
import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.statistics.DailyAggregate;
import com.svenruppert.urlshortener.core.statistics.HourlyAggregate;
import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import com.svenruppert.urlshortener.core.statistics.StatisticsConfig;
import com.svenruppert.urlshortener.core.urlmapping.ShortUrlMapping;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DataRoot
    implements Serializable , HasLogger {
  @Serial
  private static final long serialVersionUID = 6L;  // Incremented for link-ownership persistence

  private final Map<String, Map<String, Map<String, Boolean>>> columnVisibilityByUserAndView
      = new ConcurrentHashMap<>();

  private final Map<String, ShortUrlMapping> shortUrlMappingMap = new ConcurrentHashMap<>();

  // Statistics: Raw events per shortCode and date
  // Structure: shortCode -> date -> list of events
  // NOTE: Not final to allow initialization in readObject for backwards compatibility
  private Map<String, Map<String, List<RedirectEvent>>> redirectEvents
      = new ConcurrentHashMap<>();

  // Statistics: Hourly aggregates within the hot window
  // Structure: shortCode -> date -> hourly aggregate
  private Map<String, Map<String, HourlyAggregate>> hourlyAggregates
      = new ConcurrentHashMap<>();

  // Statistics: Daily aggregates (permanent)
  // Structure: shortCode -> date -> daily aggregate
  private Map<String, Map<String, DailyAggregate>> dailyAggregates
      = new ConcurrentHashMap<>();

  // Statistics configuration (hot window size, etc.)
  private StatisticsConfig statisticsConfig = new StatisticsConfig();

  // API keys: SHA-256 hash -> stored key entry (never the plaintext).
  // Not final so it can be initialized on load of a DataRoot created before
  // API-key persistence was added (see ensureStatisticsInitialized).
  private Map<String, ApiKeyEntry> apiKeys = new ConcurrentHashMap<>();

  // Link campaigns (1:many), cross-cutting tags (m2m) and per-link placement labels.
  // Not final for backward-compat init on load (see ensureStatisticsInitialized).
  private Map<String, Campaign> campaigns = new ConcurrentHashMap<>();
  private Map<String, String> campaignOfLink = new ConcurrentHashMap<>();      // shortCode -> campaignId
  private Map<String, Set<String>> linkTags = new ConcurrentHashMap<>();       // shortCode -> tags
  private Map<String, String> linkLabels = new ConcurrentHashMap<>();          // shortCode -> placement label
  private Map<String, Boolean> knownTags = new ConcurrentHashMap<>();          // tag registry (incl. empty tags)

  // Link ownership (V00.17.00 P020): normalized shortCode -> owning user id.
  // Not final for backward-compat init on load (see ensureStatisticsInitialized).
  private Map<String, Long> linkOwners = new ConcurrentHashMap<>();

  /**
   * Custom deserialization to handle DataRoot instances created before statistics were added.
   * Ensures all statistics fields are initialized even if they were null in the serialized data.
   */
  @Serial
  private void readObject(ObjectInputStream in)
      throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    ensureStatisticsInitialized();
  }

  /**
   * Ensures all statistics-related fields are initialized.
   * Called after loading from EclipseStore to handle DataRoot instances
   * that were created before statistics support was added.
   *
   * @return true if any field was initialized (storage needs update)
   */
  public boolean ensureStatisticsInitialized() {
    boolean modified = false;
    if (redirectEvents == null) {
      redirectEvents = new ConcurrentHashMap<>();
      modified = true;
    }
    if (hourlyAggregates == null) {
      hourlyAggregates = new ConcurrentHashMap<>();
      modified = true;
    }
    if (dailyAggregates == null) {
      dailyAggregates = new ConcurrentHashMap<>();
      modified = true;
    }
    if (statisticsConfig == null) {
      statisticsConfig = new StatisticsConfig();
      modified = true;
    }
    if (apiKeys == null) {
      apiKeys = new ConcurrentHashMap<>();
      modified = true;
    }
    if (campaigns == null) {
      campaigns = new ConcurrentHashMap<>();
      modified = true;
    }
    if (campaignOfLink == null) {
      campaignOfLink = new ConcurrentHashMap<>();
      modified = true;
    }
    if (linkTags == null) {
      linkTags = new ConcurrentHashMap<>();
      modified = true;
    }
    if (linkLabels == null) {
      linkLabels = new ConcurrentHashMap<>();
      modified = true;
    }
    if (knownTags == null) {
      knownTags = new ConcurrentHashMap<>();
      modified = true;
    }
    if (linkOwners == null) {
      linkOwners = new ConcurrentHashMap<>();
      modified = true;
    }
    return modified;
  }

  public Map<String, ApiKeyEntry> apiKeys() {
    return apiKeys;
  }

  public Map<String, Campaign> campaigns() {
    return campaigns;
  }

  public Map<String, String> campaignOfLink() {
    return campaignOfLink;
  }

  public Map<String, Set<String>> linkTags() {
    return linkTags;
  }

  public Map<String, String> linkLabels() {
    return linkLabels;
  }

  public Map<String, Boolean> knownTags() {
    return knownTags;
  }

  public Map<String, Long> linkOwners() {
    return linkOwners;
  }

  public Map<String, ShortUrlMapping> shortUrlMappings() {
    return shortUrlMappingMap;
  }

  public Map<String, Map<String, Map<String, Boolean>>> columnVisibilityPreferences() {
    return columnVisibilityByUserAndView;
  }

  public Map<String, Map<String, List<RedirectEvent>>> redirectEvents() {
    return redirectEvents;
  }

  public Map<String, Map<String, HourlyAggregate>> hourlyAggregates() {
    return hourlyAggregates;
  }

  public Map<String, Map<String, DailyAggregate>> dailyAggregates() {
    return dailyAggregates;
  }

  public StatisticsConfig statisticsConfig() {
    return statisticsConfig;
  }

  /**
   * Gets or creates the event list for a specific shortCode and date.
   * Uses CopyOnWriteArrayList for thread-safe append operations.
   */
  public List<RedirectEvent> getOrCreateEventList(String shortCode, LocalDate date) {
    return redirectEvents
        .computeIfAbsent(shortCode, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(date.toString(), k -> new CopyOnWriteArrayList<>());
  }

  /**
   * Gets or creates the hourly aggregate for a specific shortCode and date.
   */
  public HourlyAggregate getOrCreateHourlyAggregate(String shortCode, LocalDate date) {
    return hourlyAggregates
        .computeIfAbsent(shortCode, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(date.toString(), k -> new HourlyAggregate(date));
  }

  /**
   * Gets or creates the daily aggregate for a specific shortCode and date.
   */
  public DailyAggregate getOrCreateDailyAggregate(String shortCode, LocalDate date) {
    logger().info("getOrCreateDailyAggregate - Creating daily aggregate for shortCode={} date={}", shortCode, date);
    return dailyAggregates
        .computeIfAbsent(shortCode, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(date.toString(), k -> new DailyAggregate(date));
  }

  /**
   * Removes all statistics data for a given shortCode.
   * Called when a URL mapping is deleted.
   */
  public void removeStatisticsForShortCode(String shortCode) {
    redirectEvents.remove(shortCode);
    hourlyAggregates.remove(shortCode);
    dailyAggregates.remove(shortCode);
  }


}