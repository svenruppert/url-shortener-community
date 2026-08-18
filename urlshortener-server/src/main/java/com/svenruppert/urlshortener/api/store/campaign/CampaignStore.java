package com.svenruppert.urlshortener.api.store.campaign;

import com.svenruppert.urlshortener.core.campaign.Campaign;
import com.svenruppert.urlshortener.core.campaign.CampaignMetaExport;
import com.svenruppert.urlshortener.core.campaign.CampaignType;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Storage for link campaigns (1:many), cross-cutting tags (m2m) and per-link
 * placement labels. Reverse lookups ({@link #linksOfCampaign},
 * {@link #linksOfTag}) are derived from the authoritative forward maps, so there
 * is no index to keep consistent.
 */
public interface CampaignStore {

  Campaign create(String name, String description, CampaignType type,
                  LocalDate from, LocalDate to, Long ownerUserId);

  Optional<Campaign> get(String campaignId);

  List<Campaign> listAll();

  /** Deletes a campaign and unassigns its links (the links themselves stay). */
  boolean delete(String campaignId);

  /** Assigns a link to a campaign (1:many — replaces any previous assignment). */
  void assignLink(String shortCode, String campaignId);

  void removeLink(String shortCode);

  Optional<String> campaignIdOfLink(String shortCode);

  Set<String> linksOfCampaign(String campaignId);

  /** Replaces the tag set of a link (tags are normalized: trimmed + lowercased). */
  void setTags(String shortCode, Set<String> tags);

  Set<String> tagsOfLink(String shortCode);

  Set<String> linksOfTag(String tag);

  /**
   * All defined tags mapped to the number of links currently carrying each —
   * including registry tags that no link carries yet (count {@code 0}).
   */
  Map<String, Integer> tagCounts();

  /** Defines a tag in the registry so it can be assigned to links later (starts empty). */
  void createTag(String name);

  /** Adds a tag to each given link (union with its existing tags); registers the tag. */
  void addTagToLinks(String tag, java.util.Collection<String> shortCodes);

  /**
   * Renames a tag across every link. If {@code to} already exists on a link, the
   * rename merges (union); if {@code to} is blank/null the tag is removed (delete).
   * Both names are normalized.
   */
  void renameTag(String from, String to);

  /**
   * Replaces a tag by one or more others across every link that carries it: on
   * each such link {@code from} is removed and all {@code to} tags are added. The
   * source tag is retired from the registry; a single {@code to} equals a rename,
   * an empty {@code to} equals a delete.
   */
  void replaceTag(String from, List<String> to);

  /** Removes a tag from every link that carries it and from the registry. */
  void deleteTag(String tag);

  void setLabel(String shortCode, String label);

  Optional<String> labelOfLink(String shortCode);

  /** Cascade when a link is deleted: drop its membership, tags and label. */
  void onLinkDeleted(String shortCode);

  /** Snapshot of all campaign metadata (definitions, membership, tags, labels). */
  CampaignMetaExport exportMeta();

  /**
   * Applies an exported metadata snapshot. When {@code replace} is true the
   * current metadata is cleared first; otherwise the snapshot is merged in.
   */
  void importMeta(CampaignMetaExport data, boolean replace);

  /** Normalizes a tag (trim + lowercase); returns {@code null} for blank input. */
  static String normalizeTag(String tag) {
    if (tag == null) return null;
    String t = tag.trim().toLowerCase(Locale.ROOT);
    return t.isEmpty() ? null : t;
  }
}
