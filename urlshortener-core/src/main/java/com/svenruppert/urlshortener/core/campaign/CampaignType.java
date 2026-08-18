package com.svenruppert.urlshortener.core.campaign;

/**
 * The kind of thing a campaign groups links for. Persistence-compatibility
 * type — kept in the community edition for dormant pre-split data (see
 * {@link Campaign}); the feature lives in the enterprise link-meta plug-in.
 */
public enum CampaignType {
  PDF,
  MAGAZINE,
  EVENT,
  EMAIL,
  WEB,
  OTHER
}
