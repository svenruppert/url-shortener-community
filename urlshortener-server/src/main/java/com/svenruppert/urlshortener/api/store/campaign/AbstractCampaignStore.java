package com.svenruppert.urlshortener.api.store.campaign;

import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignMetaExport;
import com.svenruppert.urlshortener.core.campaign.CampaignType;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shared campaign logic over four authoritative maps. Concrete stores supply the
 * maps and, for the persistent store, a {@link #persist()} that writes them back
 * to storage after each mutation. Reverse lookups are derived on demand.
 */
public abstract class AbstractCampaignStore
    implements CampaignStore {

  protected abstract Map<String, Campaign> campaignsMap();

  protected abstract Map<String, String> campaignOfLinkMap();

  protected abstract Map<String, Set<String>> linkTagsMap();

  protected abstract Map<String, String> linkLabelsMap();

  /** Tag registry (keys are the defined tags, including empty ones). */
  protected abstract Map<String, Boolean> knownTagsMap();

  /** Called after every mutation; the persistent store writes the maps here. */
  protected void persist() {
  }

  @Override
  public Campaign create(String name, String description, CampaignType type,
                         LocalDate from, LocalDate to, Long ownerUserId) {
    String id = "cmp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Campaign campaign = new Campaign(id, name, description,
        type == null ? CampaignType.OTHER : type, from, to, ownerUserId,
        Instant.now(Clock.systemUTC()));
    campaignsMap().put(id, campaign);
    persist();
    return campaign;
  }

  @Override
  public Optional<Campaign> get(String campaignId) {
    return Optional.ofNullable(campaignsMap().get(campaignId));
  }

  @Override
  public List<Campaign> listAll() {
    return new ArrayList<>(campaignsMap().values());
  }

  @Override
  public boolean delete(String campaignId) {
    boolean removed = campaignsMap().remove(campaignId) != null;
    if (removed) {
      campaignOfLinkMap().entrySet().removeIf(e -> campaignId.equals(e.getValue()));
      persist();
    }
    return removed;
  }

  @Override
  public void assignLink(String shortCode, String campaignId) {
    campaignOfLinkMap().put(shortCode, campaignId); // 1:many — replaces
    persist();
  }

  @Override
  public void removeLink(String shortCode) {
    if (campaignOfLinkMap().remove(shortCode) != null) persist();
  }

  @Override
  public Optional<String> campaignIdOfLink(String shortCode) {
    return Optional.ofNullable(campaignOfLinkMap().get(shortCode));
  }

  @Override
  public Set<String> linksOfCampaign(String campaignId) {
    return campaignOfLinkMap().entrySet().stream()
        .filter(e -> campaignId.equals(e.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public void setTags(String shortCode, Set<String> tags) {
    Set<String> normalized = new LinkedHashSet<>();
    if (tags != null) {
      for (String t : tags) {
        String n = CampaignStore.normalizeTag(t);
        if (n != null) normalized.add(n);
      }
    }
    if (normalized.isEmpty()) {
      linkTagsMap().remove(shortCode);
    } else {
      linkTagsMap().put(shortCode, normalized);
      for (String n : normalized) knownTagsMap().put(n, Boolean.TRUE); // auto-register assigned tags
    }
    persist();
  }

  @Override
  public Set<String> tagsOfLink(String shortCode) {
    return new LinkedHashSet<>(linkTagsMap().getOrDefault(shortCode, Set.of()));
  }

  @Override
  public Set<String> linksOfTag(String tag) {
    String n = CampaignStore.normalizeTag(tag);
    if (n == null) return Set.of();
    return linkTagsMap().entrySet().stream()
        .filter(e -> e.getValue().contains(n))
        .map(Map.Entry::getKey)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public Map<String, Integer> tagCounts() {
    Map<String, Integer> counts = new TreeMap<>();
    for (String t : knownTagsMap().keySet()) counts.put(t, 0);      // registry tags start at 0
    for (Set<String> tags : linkTagsMap().values()) {
      for (String t : tags) counts.merge(t, 1, Integer::sum);       // union: also counts legacy tags not yet registered
    }
    return counts;
  }

  @Override
  public void createTag(String name) {
    String n = CampaignStore.normalizeTag(name);
    if (n == null) return;
    if (knownTagsMap().putIfAbsent(n, Boolean.TRUE) == null) persist();
  }

  @Override
  public void addTagToLinks(String tag, java.util.Collection<String> shortCodes) {
    String n = CampaignStore.normalizeTag(tag);
    if (n == null || shortCodes == null) return;
    knownTagsMap().put(n, Boolean.TRUE); // ensure the tag is registered
    for (String code : shortCodes) {
      if (code == null || code.isBlank()) continue;
      Set<String> existing = linkTagsMap().get(code);
      Set<String> updated = existing == null ? new LinkedHashSet<>() : new LinkedHashSet<>(existing);
      if (updated.add(n)) linkTagsMap().put(code, updated); // union, replace with a fresh set (persist-safe)
    }
    persist();
  }

  @Override
  public void renameTag(String from, String to) {
    String t = CampaignStore.normalizeTag(to);
    replaceTag(from, t == null ? List.of() : List.of(t));
  }

  @Override
  public void replaceTag(String from, List<String> to) {
    String f = CampaignStore.normalizeTag(from);
    if (f == null) return;
    Set<String> targets = new LinkedHashSet<>();
    if (to != null) {
      for (String t : to) {
        String n = CampaignStore.normalizeTag(t);
        if (n != null) targets.add(n);
      }
    }
    // Rewrite every link that carries `from` (replace fresh sets — persist-safe).
    for (Map.Entry<String, Set<String>> e : new ArrayList<>(linkTagsMap().entrySet())) {
      if (!e.getValue().contains(f)) continue;
      Set<String> updated = new LinkedHashSet<>(e.getValue());
      updated.remove(f);
      updated.addAll(targets);
      if (updated.isEmpty()) {
        linkTagsMap().remove(e.getKey());
      } else {
        linkTagsMap().put(e.getKey(), updated);
      }
    }
    // Registry: retire the source (unless it is also a target) and register the targets.
    if (!targets.contains(f)) knownTagsMap().remove(f);
    for (String t : targets) knownTagsMap().put(t, Boolean.TRUE);
    persist();
  }

  @Override
  public void deleteTag(String tag) {
    replaceTag(tag, List.of()); // remove `tag` from every link and from the registry
  }

  @Override
  public void setLabel(String shortCode, String label) {
    if (label == null || label.isBlank()) {
      linkLabelsMap().remove(shortCode);
    } else {
      linkLabelsMap().put(shortCode, label.trim());
    }
    persist();
  }

  @Override
  public Optional<String> labelOfLink(String shortCode) {
    return Optional.ofNullable(linkLabelsMap().get(shortCode));
  }

  @Override
  public CampaignMetaExport exportMeta() {
    List<Campaign> camps = new ArrayList<>(campaignsMap().values());
    Map<String, String> membership = new LinkedHashMap<>(campaignOfLinkMap());
    Map<String, List<String>> tags = new LinkedHashMap<>();
    for (Map.Entry<String, Set<String>> e : linkTagsMap().entrySet()) {
      tags.put(e.getKey(), new ArrayList<>(e.getValue()));
    }
    Map<String, String> labels = new LinkedHashMap<>(linkLabelsMap());
    List<String> registry = new ArrayList<>(knownTagsMap().keySet());
    return new CampaignMetaExport(CampaignMetaExport.FORMAT_VERSION, camps, membership, tags, labels, registry);
  }

  @Override
  public void importMeta(CampaignMetaExport data, boolean replace) {
    if (data == null) return;
    if (replace) {
      campaignsMap().clear();
      campaignOfLinkMap().clear();
      linkTagsMap().clear();
      linkLabelsMap().clear();
      knownTagsMap().clear();
    }
    if (data.campaigns() != null) {
      for (Campaign c : data.campaigns()) {
        if (c != null && c.id() != null) campaignsMap().put(c.id(), c);
      }
    }
    if (data.campaignOfLink() != null) campaignOfLinkMap().putAll(data.campaignOfLink());
    if (data.linkTags() != null) {
      for (Map.Entry<String, List<String>> e : data.linkTags().entrySet()) {
        Set<String> normalized = new LinkedHashSet<>();
        if (e.getValue() != null) {
          for (String t : e.getValue()) {
            String n = CampaignStore.normalizeTag(t);
            if (n != null) normalized.add(n);
          }
        }
        if (!normalized.isEmpty()) {
          linkTagsMap().put(e.getKey(), normalized);
          for (String n : normalized) knownTagsMap().put(n, Boolean.TRUE);
        }
      }
    }
    if (data.linkLabels() != null) linkLabelsMap().putAll(data.linkLabels());
    if (data.knownTags() != null) {
      for (String t : data.knownTags()) {
        String n = CampaignStore.normalizeTag(t);
        if (n != null) knownTagsMap().put(n, Boolean.TRUE);
      }
    }
    persist();
  }

  @Override
  public void onLinkDeleted(String shortCode) {
    boolean changed = campaignOfLinkMap().remove(shortCode) != null;
    changed |= linkTagsMap().remove(shortCode) != null;
    changed |= linkLabelsMap().remove(shortCode) != null;
    if (changed) persist();
  }
}
