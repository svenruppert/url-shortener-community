package com.svenruppert.urlshortener.core.urlmapping;




import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ShortUrlMapping {
  private  String shortCode;
  private  String originalUrl;
  private  Instant createdAt;
  private  Instant expiresAt;
  private  boolean active;
  private  String passwordHash;

  public ShortUrlMapping() {
  }

  public ShortUrlMapping(
      String shortCode,
      String originalUrl,
      Instant createdAt,
      Instant expiresAt,
      boolean active
  ) {
    this(shortCode, originalUrl, createdAt, expiresAt, active, null);
  }

  public ShortUrlMapping(
      String shortCode,
      String originalUrl,
      Instant createdAt,
      Instant expiresAt,
      boolean active,
      String passwordHash
  ) {
    this.shortCode = shortCode;
    this.originalUrl = originalUrl;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.active = active;
    this.passwordHash = passwordHash;
  }

  public String getShortCode() {
    return shortCode;
  }

  public String getOriginalUrl() {
    return originalUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isActive() {
    return active;
  }

  /**
   * Raw stored hash of the per-link password; {@code null} = no password protection.
   * {@code @JsonIgnore} keeps the hash out of every JSON response (create/list/details)
   * and any JSON export — it only ever lives in the binary Eclipse-Store graph.
   */
  @JsonIgnore
  public String getPasswordHash() {
    return passwordHash;
  }

  public String shortCode() {
    return shortCode;
  }

  public String originalUrl() {
    return originalUrl;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> expiresAt() {
    return Optional.ofNullable(expiresAt);
  }

  public boolean active() {
    return active;
  }

  /** Present when the link is password-protected; the value is the opaque stored hash. */
  public Optional<String> passwordHash() {
    return Optional.ofNullable(passwordHash);
  }

  // --- COPY-WITH methods ---
  public ShortUrlMapping withShortCode(String shortCode) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, passwordHash);
  }

  public ShortUrlMapping withOriginalUrl(String originalUrl) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, passwordHash);
  }

  public ShortUrlMapping withCreatedAt(Instant createdAt) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, passwordHash);
  }

  public ShortUrlMapping withExpiresAt(Instant expiresAt) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, passwordHash);
  }

  public ShortUrlMapping withActive(boolean active) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, passwordHash);
  }

  public ShortUrlMapping withPasswordHash(String passwordHash) {
    return new ShortUrlMapping(shortCode, originalUrl, createdAt, expiresAt, active, passwordHash);
  }


  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ShortUrlMapping) obj;
    return Objects.equals(this.shortCode, that.shortCode) &&
        Objects.equals(this.originalUrl, that.originalUrl) &&
        Objects.equals(this.createdAt, that.createdAt) &&
        Objects.equals(this.expiresAt, that.expiresAt) &&
        this.active == that.active &&
        Objects.equals(this.passwordHash, that.passwordHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(shortCode, originalUrl, createdAt, expiresAt, active, passwordHash);
  }

  @Override
  public String toString() {
    return "ShortUrlMapping[" +
        "shortCode=" + shortCode + ", " +
        "originalUrl=" + originalUrl + ", " +
        "createdAt=" + createdAt + ", " +
        "expiresAt=" + expiresAt + ", " +
        "active=" + active + ", " +
        "passwordProtected=" + (passwordHash != null) +
        ']';
  }

}
