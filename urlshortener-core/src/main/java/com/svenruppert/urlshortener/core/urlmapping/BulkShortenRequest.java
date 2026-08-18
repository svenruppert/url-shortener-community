package com.svenruppert.urlshortener.core.urlmapping;





import java.time.Instant;
import java.util.List;

/**
 * Request DTO for the bulk short-link creation endpoint.
 *
 * <p>Only {@code urls} is mandatory. The two optional default fields apply uniformly
 * to every URL in the batch and keep the basic invocation simple while opening the
 * contract for common campaign-style use cases:
 *
 * <ul>
 *   <li>{@code defaultExpiresAt} – if set, every created link gets this expiry timestamp.
 *       When absent the links have no expiry.</li>
 *   <li>{@code defaultActive} – controls whether all created links start in an active or
 *       inactive state.  Defaults to {@code true} when absent so that links are
 *       immediately usable, matching the original bulk-create intent.</li>
 * </ul>
 */
public final class BulkShortenRequest {

  /** Maximum number of URLs accepted in a single request. */
  public static final int MAX_URLS = 500;

  /** Maximum character length of a single URL entry. */
  public static final int MAX_URL_LENGTH = 2_000;

  private List<String> urls;
  private Instant defaultExpiresAt;
  private Boolean defaultActive;
  // Optional create-time campaign metadata applied to every link in the batch.
  private String defaultCampaignId;
  private List<String> defaultTags;
  private String defaultLabel;

  public BulkShortenRequest() {
  }

  public BulkShortenRequest(List<String> urls) {
    this.urls = urls;
  }

  public BulkShortenRequest(List<String> urls, Instant defaultExpiresAt, Boolean defaultActive) {
    this.urls = urls;
    this.defaultExpiresAt = defaultExpiresAt;
    this.defaultActive = defaultActive;
  }

  public BulkShortenRequest(List<String> urls, Instant defaultExpiresAt, Boolean defaultActive,
                            String defaultCampaignId, List<String> defaultTags, String defaultLabel) {
    this(urls, defaultExpiresAt, defaultActive);
    this.defaultCampaignId = defaultCampaignId;
    this.defaultTags = defaultTags;
    this.defaultLabel = defaultLabel;
  }

  // ── Accessors ──────────────────────────────────────────────────────────────

  public List<String> getUrls() {
    return urls;
  }

  public void setUrls(List<String> urls) {
    this.urls = urls;
  }

  /**
   * Optional expiry timestamp applied to every link in this batch.
   * {@code null} means no expiry.
   */
  public Instant getDefaultExpiresAt() {
    return defaultExpiresAt;
  }

  public void setDefaultExpiresAt(Instant defaultExpiresAt) {
    this.defaultExpiresAt = defaultExpiresAt;
  }

  /**
   * Optional active-state applied to every link in this batch.
   * {@code null} is treated as {@code true} by the server.
   */
  public Boolean getDefaultActive() {
    return defaultActive;
  }

  public void setDefaultActive(Boolean defaultActive) {
    this.defaultActive = defaultActive;
  }

  /** Returns the effective active flag, defaulting to {@code true} when absent. */
  public boolean effectiveActive() {
    return defaultActive == null || defaultActive;
  }

  /** Optional campaign every created link in this batch is assigned to. */
  public String getDefaultCampaignId() {
    return defaultCampaignId;
  }

  public void setDefaultCampaignId(String defaultCampaignId) {
    this.defaultCampaignId = defaultCampaignId;
  }

  /** Optional tag set applied to every created link in this batch. */
  public List<String> getDefaultTags() {
    return defaultTags;
  }

  public void setDefaultTags(List<String> defaultTags) {
    this.defaultTags = defaultTags;
  }

  /** Optional placement label applied to every created link in this batch. */
  public String getDefaultLabel() {
    return defaultLabel;
  }

  public void setDefaultLabel(String defaultLabel) {
    this.defaultLabel = defaultLabel;
  }
}