package com.svenruppert.urlshortener.core.urlmapping;


import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;
import java.util.Objects;

/**
 * Create request for a single link. Plug-in metadata (campaign/tags/label)
 * no longer lives here (V00.18.00 P085, D2) — plug-ins parse their own
 * fields from the raw request JSON; unknown fields are ignored on parse.
 *
 * <p>{@code password} is the optional per-link password in clear text (it travels
 * over HTTPS and is hashed server-side before storage). {@code null} means the
 * link is not password-protected. On edit the field carries dedicated null
 * semantics — see {@code EditMappingHandler}.
 */
public final class ShortenRequest {
  private String url;
  @JsonAlias("alias")
  private String shortURL;
  private Instant expiresAt;
  private Boolean active;
  private String password;

  public ShortenRequest() {
  }

  public ShortenRequest(String url, String shortURL, Instant expiresAt, Boolean active) {
    this(url, shortURL, expiresAt, active, null);
  }

  public ShortenRequest(String url, String shortURL, Instant expiresAt, Boolean active, String password) {
    this.url = url;
    this.shortURL = shortURL;
    this.expiresAt = expiresAt;
    this.active = active;
    this.password = password;
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

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public String toString() {
    return "ShortenRequest{" +
        "url='" + url + '\'' +
        ", shortURL='" + shortURL + '\'' +
        ", expiresAt='" + expiresAt + '\'' +
        ", active='" + active + '\'' +
        ", password='" + (password == null ? null : "***") + '\'' +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ShortenRequest that = (ShortenRequest) o;
    return Objects.equals(url, that.url) && Objects.equals(shortURL, that.shortURL) && Objects.equals(expiresAt, that.expiresAt) && Objects.equals(active, that.active) && Objects.equals(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, shortURL, expiresAt, active, password);
  }
}
