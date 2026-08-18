package com.svenruppert.urlshortener.core.urlmapping;





import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ShortenRequest {
  private String url;
  @JsonAlias("alias")
  private String shortURL;
  private Instant expiresAt;
  private Boolean active;
  // Optional create-time campaign metadata (assigned server-side after creation).
  private String campaignId;
  private List<String> tags;
  private String label;

  public ShortenRequest() {
  }

  public ShortenRequest(String url, String shortURL, Instant expiresAt, Boolean active) {
    this.url = url;
    this.shortURL = shortURL;
    this.expiresAt = expiresAt;
    this.active = active;
  }

  public ShortenRequest(String url, String shortURL, Instant expiresAt, Boolean active,
                        String campaignId, List<String> tags, String label) {
    this(url, shortURL, expiresAt, active);
    this.campaignId = campaignId;
    this.tags = tags;
    this.label = label;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getShortURL() {
    return shortURL;
  }

  public void setShortURL(String shortURL) {
    this.shortURL = shortURL;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public boolean hasAlias() {
    return shortURL != null && !shortURL.isBlank();
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public String getCampaignId() {
    return campaignId;
  }

  public void setCampaignId(String campaignId) {
    this.campaignId = campaignId;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return "ShortenRequest{" +
        "url='" + url + '\'' +
        ", shortURL='" + shortURL + '\'' +
        ", expiresAt='" + expiresAt + '\'' +
        ", active='" + active + '\'' +
        ", campaignId='" + campaignId + '\'' +
        ", tags=" + tags +
        ", label='" + label + '\'' +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ShortenRequest that = (ShortenRequest) o;
    return Objects.equals(url, that.url) && Objects.equals(shortURL, that.shortURL) && Objects.equals(expiresAt, that.expiresAt) && Objects.equals(active, that.active);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, shortURL, expiresAt, active);
  }
}