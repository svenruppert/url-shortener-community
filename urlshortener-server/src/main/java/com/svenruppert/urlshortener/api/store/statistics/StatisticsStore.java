package com.svenruppert.urlshortener.api.store.statistics;

import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import com.svenruppert.urlshortener.core.statistics.StatisticsConfig;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Combined interface for statistics storage operations.
 * Extends both reader and writer interfaces and adds administrative methods.
 */
public interface StatisticsStore extends StatisticsReader, StatisticsWriter {

  /**
   * Returns the current statistics configuration.
   */
  StatisticsConfig getConfig();

  /**
   * Updates the statistics configuration.
   * Changes are persisted immediately.
   *
   * @param config the new configuration
   */
  void updateConfig(StatisticsConfig config);

  /**
   * Removes all statistics data for a given short code.
   * Called when a URL mapping is deleted.
   *
   * @param shortCode the short code to remove
   */
  void removeAllForShortCode(String shortCode);

  /**
   * Starts background processing threads (writer, aggregator).
   * Should be called once during application startup.
   */
  void start();

  /**
   * Stops background processing threads gracefully.
   * Should be called during application shutdown.
   */
  void stop();

  /**
   * Returns debug information about the internal state of the statistics store.
   * Used for debugging persistence issues.
   */
  default Map<String, Object> getDebugInfo() {
    return Map.of("message", "Debug info not implemented for this store type");
  }

  /**
   * Rebuilds hourly and daily aggregates from raw redirect events for the
   * inclusive date range. Existing aggregates within that range are
   * discarded first to avoid double counting; raw events stay untouched.
   *
   * @param from inclusive start date
   * @param to   inclusive end date
   * @return number of (shortCode, date) buckets rebuilt
   */
  default long reaggregate(LocalDate from, LocalDate to) {
    return 0;
  }

  /**
   * Imports raw redirect events idempotently: an event is added only if an
   * equal event (by value — {@link RedirectEvent#equals}) was <em>not already
   * present before this import</em>, so re-importing the same data is a no-op
   * (fixes double-counted aggregates). Identical-looking events <em>within the
   * same import</em> are all kept — they are distinct events that merely share
   * their field values (e.g. minimal events at the same coarse timestamp).
   * Aggregates are NOT updated here; call
   * {@link #reaggregate(LocalDate, LocalDate)} afterwards to rebuild them from
   * the raw events.
   *
   * <p>Implemented generically via {@link #getEventsForDate},
   * {@link #recordEvent} and {@link #flush} — works for any store. The
   * pre-import snapshot per {@code (shortCode, date)} is captured lazily on
   * first sight and never mutated, so the check is always against the state
   * that existed <em>before</em> this import.
   *
   * @param events the events to import
   * @return the number of NEW events actually added (imported minus already-present)
   */
  default long importEventsDeduplicated(Collection<RedirectEvent> events) {
    flush(); // make already-persisted events visible to the de-dup check
    Map<String, Map<LocalDate, Set<RedirectEvent>>> preexisting = new HashMap<>();
    long added = 0;
    for (RedirectEvent event : events) {
      LocalDate date = event.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
      Set<RedirectEvent> before = preexisting
          .computeIfAbsent(event.shortCode(), k -> new HashMap<>())
          .computeIfAbsent(date, d -> new HashSet<>(getEventsForDate(event.shortCode(), d)));
      if (!before.contains(event)) {
        recordEvent(event);
        added++;
      }
    }
    flush();
    return added;
  }
}
