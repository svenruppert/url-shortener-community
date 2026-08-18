package junit.com.svenruppert.urlshortener.api.provider.inmemory;

import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryStatisticsStore;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import com.svenruppert.urlshortener.core.statistics.StatisticsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for statistics debug functionality.
 * Verifies that getDebugInfo() returns correct information about the store state.
 */
class StatisticsDebugTest {

  private InMemoryStatisticsStore store;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    // Fixed clock at 2024-01-15 12:00:00 UTC
    Instant fixedInstant = LocalDateTime.of(2024, 1, 15, 12, 0, 0)
        .toInstant(ZoneOffset.UTC);
    fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    store = new InMemoryStatisticsStore(fixedClock);
  }

  @Nested
  @DisplayName("getDebugInfo() on empty store")
  class EmptyStore {

    @Test
    @DisplayName("should return config information")
    void shouldReturnConfigInfo() {
      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals(true, debugInfo.get("statisticsEnabled"));
      assertEquals(StatisticsConfig.DEFAULT_HOT_WINDOW_DAYS, debugInfo.get("hotWindowDays"));
      assertEquals(StatisticsConfig.DEFAULT_WRITER_BATCH_SIZE, debugInfo.get("writerBatchSize"));
      assertEquals(StatisticsConfig.DEFAULT_AGGREGATOR_INTERVAL_SECONDS, debugInfo.get("aggregatorIntervalSeconds"));
    }

    @Test
    @DisplayName("should return store type")
    void shouldReturnStoreType() {
      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals("InMemory", debugInfo.get("storeType"));
    }

    @Test
    @DisplayName("should return zero counts for empty store")
    void shouldReturnZeroCounts() {
      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals(0, debugInfo.get("events_shortCodeCount"));
      assertEquals(0, debugInfo.get("hourlyAggregates_shortCodeCount"));
      assertEquals(0, debugInfo.get("dailyAggregates_shortCodeCount"));
      assertEquals(0L, debugInfo.get("totalEventCount"));
      assertEquals(0, debugInfo.get("shortCodeCount"));
    }

    @Test
    @DisplayName("should not contain detail keys when empty")
    void shouldNotContainDetailKeysWhenEmpty() {
      Map<String, Object> debugInfo = store.getDebugInfo();

      assertFalse(debugInfo.containsKey("events_detail"));
      assertFalse(debugInfo.containsKey("hourlyAggregates_detail"));
      assertFalse(debugInfo.containsKey("dailyAggregates_detail"));
    }
  }

  @Nested
  @DisplayName("getDebugInfo() with recorded events")
  class WithEvents {

    @Test
    @DisplayName("should return correct short code count after recording events")
    void shouldReturnCorrectShortCodeCount() {
      recordEvent("code1", 2024, 1, 15, 10, 0);
      recordEvent("code2", 2024, 1, 15, 11, 0);
      recordEvent("code3", 2024, 1, 15, 12, 0);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals(3, debugInfo.get("events_shortCodeCount"));
      assertEquals(3, debugInfo.get("hourlyAggregates_shortCodeCount"));
      assertEquals(3, debugInfo.get("dailyAggregates_shortCodeCount"));
      assertEquals(3, debugInfo.get("shortCodeCount"));
    }

    @Test
    @DisplayName("should return correct total event count")
    void shouldReturnCorrectTotalEventCount() {
      recordEvent("code1", 2024, 1, 15, 10, 0);
      recordEvent("code1", 2024, 1, 15, 11, 0);
      recordEvent("code1", 2024, 1, 15, 12, 0);
      recordEvent("code2", 2024, 1, 15, 10, 0);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals(4L, debugInfo.get("totalEventCount"));
    }

    @Test
    @DisplayName("should contain events_detail with correct structure")
    @SuppressWarnings("unchecked")
    void shouldContainEventsDetailWithCorrectStructure() {
      recordEvent("abc123", 2024, 1, 15, 10, 0);
      recordEvent("abc123", 2024, 1, 15, 11, 0);
      recordEvent("abc123", 2024, 1, 14, 9, 0);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertTrue(debugInfo.containsKey("events_detail"));

      Map<String, Object> eventsDetail = (Map<String, Object>) debugInfo.get("events_detail");
      assertTrue(eventsDetail.containsKey("abc123"));

      Map<String, Integer> dateDetail = (Map<String, Integer>) eventsDetail.get("abc123");
      assertEquals(2, dateDetail.get("2024-01-15"));
      assertEquals(1, dateDetail.get("2024-01-14"));
    }

    @Test
    @DisplayName("should contain hourlyAggregates_detail with correct counts")
    @SuppressWarnings("unchecked")
    void shouldContainHourlyAggregatesDetailWithCorrectCounts() {
      recordEvent("abc123", 2024, 1, 15, 10, 0);
      recordEvent("abc123", 2024, 1, 15, 10, 30);
      recordEvent("abc123", 2024, 1, 15, 14, 0);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertTrue(debugInfo.containsKey("hourlyAggregates_detail"));

      Map<String, Object> hourlyDetail = (Map<String, Object>) debugInfo.get("hourlyAggregates_detail");
      assertTrue(hourlyDetail.containsKey("abc123"));

      Map<String, Long> dateDetail = (Map<String, Long>) hourlyDetail.get("abc123");
      assertEquals(3L, dateDetail.get("2024-01-15")); // Total count for the day
    }

    @Test
    @DisplayName("should contain dailyAggregates_detail with correct counts")
    @SuppressWarnings("unchecked")
    void shouldContainDailyAggregatesDetailWithCorrectCounts() {
      recordEvent("abc123", 2024, 1, 15, 10, 0);
      recordEvent("abc123", 2024, 1, 15, 11, 0);
      recordEvent("abc123", 2024, 1, 14, 10, 0);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertTrue(debugInfo.containsKey("dailyAggregates_detail"));

      Map<String, Object> dailyDetail = (Map<String, Object>) debugInfo.get("dailyAggregates_detail");
      assertTrue(dailyDetail.containsKey("abc123"));

      Map<String, Long> dateDetail = (Map<String, Long>) dailyDetail.get("abc123");
      assertEquals(2L, dateDetail.get("2024-01-15"));
      assertEquals(1L, dateDetail.get("2024-01-14"));
    }

    @Test
    @DisplayName("should track multiple short codes independently")
    @SuppressWarnings("unchecked")
    void shouldTrackMultipleShortCodesIndependently() {
      recordEvent("code1", 2024, 1, 15, 10, 0);
      recordEvent("code1", 2024, 1, 15, 11, 0);
      recordEvent("code2", 2024, 1, 15, 10, 0);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals(2, debugInfo.get("shortCodeCount"));
      assertEquals(3L, debugInfo.get("totalEventCount"));

      Map<String, Object> dailyDetail = (Map<String, Object>) debugInfo.get("dailyAggregates_detail");

      Map<String, Long> code1Detail = (Map<String, Long>) dailyDetail.get("code1");
      assertEquals(2L, code1Detail.get("2024-01-15"));

      Map<String, Long> code2Detail = (Map<String, Long>) dailyDetail.get("code2");
      assertEquals(1L, code2Detail.get("2024-01-15"));
    }
  }

  @Nested
  @DisplayName("getDebugInfo() with modified config")
  class WithModifiedConfig {

    @Test
    @DisplayName("should reflect disabled statistics")
    void shouldReflectDisabledStatistics() {
      StatisticsConfig config = new StatisticsConfig();
      config.setStatisticsEnabled(false);
      store.updateConfig(config);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals(false, debugInfo.get("statisticsEnabled"));
    }

    @Test
    @DisplayName("should reflect changed hot window days")
    void shouldReflectChangedHotWindowDays() {
      StatisticsConfig config = new StatisticsConfig();
      config.setHotWindowDays(14);
      store.updateConfig(config);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals(14, debugInfo.get("hotWindowDays"));
    }

    @Test
    @DisplayName("should reflect changed writer batch size")
    void shouldReflectChangedWriterBatchSize() {
      StatisticsConfig config = new StatisticsConfig();
      config.setWriterBatchSize(50);
      store.updateConfig(config);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals(50, debugInfo.get("writerBatchSize"));
    }

    @Test
    @DisplayName("should reflect changed aggregator interval")
    void shouldReflectChangedAggregatorInterval() {
      StatisticsConfig config = new StatisticsConfig();
      config.setAggregatorIntervalSeconds(1800);
      store.updateConfig(config);

      Map<String, Object> debugInfo = store.getDebugInfo();

      assertEquals(1800, debugInfo.get("aggregatorIntervalSeconds"));
    }
  }

  @Nested
  @DisplayName("getDebugInfo() after clearing data")
  class AfterClearingData {

    @Test
    @DisplayName("should return zero counts after clear()")
    void shouldReturnZeroCountsAfterClear() {
      recordEvent("code1", 2024, 1, 15, 10, 0);
      recordEvent("code2", 2024, 1, 15, 11, 0);

      // Verify data exists
      Map<String, Object> beforeClear = store.getDebugInfo();
      assertEquals(2, beforeClear.get("shortCodeCount"));

      // Clear and verify
      store.clear();

      Map<String, Object> afterClear = store.getDebugInfo();
      assertEquals(0, afterClear.get("shortCodeCount"));
      assertEquals(0L, afterClear.get("totalEventCount"));
      assertEquals(0, afterClear.get("events_shortCodeCount"));
    }

    @Test
    @DisplayName("should return zero counts after removeAllForShortCode()")
    void shouldReturnZeroCountsAfterRemoveAllForShortCode() {
      recordEvent("code1", 2024, 1, 15, 10, 0);
      recordEvent("code1", 2024, 1, 15, 11, 0);
      recordEvent("code2", 2024, 1, 15, 10, 0);

      // Remove one short code
      store.removeAllForShortCode("code1");

      Map<String, Object> debugInfo = store.getDebugInfo();
      assertEquals(1, debugInfo.get("shortCodeCount"));
      assertEquals(1L, debugInfo.get("totalEventCount"));
    }
  }

  @Nested
  @DisplayName("Default implementation in StatisticsStore interface")
  class DefaultImplementation {

    @Test
    @DisplayName("default getDebugInfo() should return message")
    void defaultGetDebugInfoShouldReturnMessage() {
      // Create a minimal StatisticsStore that only implements required methods
      StatisticsStore minimalStore = new MinimalStatisticsStore();

      Map<String, Object> debugInfo = minimalStore.getDebugInfo();

      assertTrue(debugInfo.containsKey("message"));
      assertEquals("Debug info not implemented for this store type", debugInfo.get("message"));
    }
  }

  // Helper method to record events with specific timestamp
  private void recordEvent(String shortCode, int year, int month, int day, int hour, int minute) {
    Instant timestamp = LocalDateTime.of(year, month, day, hour, minute, 0)
        .toInstant(ZoneOffset.UTC);
    store.recordEvent(RedirectEvent.minimal(shortCode, timestamp));
  }

  /**
   * Minimal StatisticsStore implementation for testing default interface method.
   */
  private static class MinimalStatisticsStore implements StatisticsStore {
    private final StatisticsConfig config = new StatisticsConfig();

    @Override
    public void recordEvent(RedirectEvent event) {}

    @Override
    public void flush() {}

    @Override
    public long getCountForDate(String shortCode, java.time.LocalDate date) { return 0; }

    @Override
    public long getCountForDateRange(String shortCode, java.time.LocalDate from, java.time.LocalDate to) { return 0; }

    @Override
    public long getTotalCount(String shortCode) { return 0; }

    @Override
    public java.util.Optional<com.svenruppert.urlshortener.core.statistics.HourlyAggregate> getHourlyAggregate(String shortCode, java.time.LocalDate date) {
      return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<com.svenruppert.urlshortener.core.statistics.DailyAggregate> getDailyAggregate(String shortCode, java.time.LocalDate date) {
      return java.util.Optional.empty();
    }

    @Override
    public java.util.List<com.svenruppert.urlshortener.core.statistics.DailyAggregate> getDailyAggregates(String shortCode, java.time.LocalDate from, java.time.LocalDate to) {
      return java.util.Collections.emptyList();
    }

    @Override
    public java.util.List<com.svenruppert.urlshortener.core.statistics.HourlyAggregate> getHourlyAggregates(String shortCode, java.time.LocalDate from, java.time.LocalDate to) {
      return java.util.Collections.emptyList();
    }

    @Override
    public java.util.List<RedirectEvent> getEventsForDate(String shortCode, java.time.LocalDate date) {
      return java.util.Collections.emptyList();
    }

    @Override
    public java.util.List<RedirectEvent> getEventsForDateRange(String shortCode, java.time.LocalDate from, java.time.LocalDate to) {
      return java.util.Collections.emptyList();
    }

    @Override
    public java.util.List<java.time.LocalDate> getAvailableDates(String shortCode) {
      return java.util.Collections.emptyList();
    }

    @Override
    public boolean isInHotWindow(java.time.LocalDate date) { return false; }

    @Override
    public StatisticsConfig getConfig() { return config; }

    @Override
    public void updateConfig(StatisticsConfig config) {}

    @Override
    public void removeAllForShortCode(String shortCode) {}

    @Override
    public void start() {}

    @Override
    public void stop() {}
  }
}