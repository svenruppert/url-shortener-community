package com.svenruppert.urlshortener.core.statistics;





import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single redirect event when a short URL is resolved to its target URL.
 * This class captures the timestamp and request metadata for analytics purposes.
 * Designed to be stored permanently in EclipseStore for historical analysis.
 */
public final class RedirectEvent implements Serializable {

  @Serial
  private static final long serialVersionUID = 2L;  // Incremented for the bot flag

  private final String shortCode;
  private final Instant timestamp;
  private final String userAgent;
  private final String referer;
  private final String ipHash;
  private final String acceptLanguage;
  private final boolean bot;

  /**
   * Convenience constructor: the bot flag is derived from the User-Agent via
   * {@link UserAgentClassifier}. Existing call sites and imports keep working
   * and get bot classification for free.
   */
  public RedirectEvent(
      String shortCode,
      Instant timestamp,
      String userAgent,
      String referer,
      String ipHash,
      String acceptLanguage
  ) {
    this(shortCode, timestamp, userAgent, referer, ipHash, acceptLanguage,
        UserAgentClassifier.isBot(userAgent));
  }

  public RedirectEvent(
      String shortCode,
      Instant timestamp,
      String userAgent,
      String referer,
      String ipHash,
      String acceptLanguage,
      boolean bot
  ) {
    this.shortCode = Objects.requireNonNull(shortCode, "shortCode must not be null");
    this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    this.userAgent = userAgent;
    this.referer = referer;
    this.ipHash = ipHash;
    this.acceptLanguage = acceptLanguage;
    this.bot = bot;
  }

  /**
   * Creates a minimal event with only the required fields (human by default).
   */
  public static RedirectEvent minimal(String shortCode, Instant timestamp) {
    return new RedirectEvent(shortCode, timestamp, null, null, null, null);
  }

  public String shortCode() {
    return shortCode;
  }

  public Instant timestamp() {
    return timestamp;
  }

  public String userAgent() {
    return userAgent;
  }

  public String referer() {
    return referer;
  }

  public String ipHash() {
    return ipHash;
  }

  public String acceptLanguage() {
    return acceptLanguage;
  }

  /** Whether this redirect was classified as bot/crawler traffic (excluded from human aggregates). */
  public boolean bot() {
    return bot;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RedirectEvent that = (RedirectEvent) o;
    return bot == that.bot
        && Objects.equals(shortCode, that.shortCode)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(userAgent, that.userAgent)
        && Objects.equals(referer, that.referer)
        && Objects.equals(ipHash, that.ipHash)
        && Objects.equals(acceptLanguage, that.acceptLanguage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(shortCode, timestamp, userAgent, referer, ipHash, acceptLanguage, bot);
  }

  @Override
  public String toString() {
    return "RedirectEvent[" +
        "shortCode=" + shortCode +
        ", timestamp=" + timestamp +
        ", userAgent=" + userAgent +
        ", referer=" + referer +
        ", ipHash=" + ipHash +
        ", acceptLanguage=" + acceptLanguage +
        ", bot=" + bot +
        ']';
  }
}