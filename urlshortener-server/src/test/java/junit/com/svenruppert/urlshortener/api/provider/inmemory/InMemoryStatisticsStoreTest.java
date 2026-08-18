package junit.com.svenruppert.urlshortener.api.store.provider.inmemory;

import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryStatisticsStore;
import com.svenruppert.urlshortener.core.statistics.DailyAggregate;
import com.svenruppert.urlshortener.core.statistics.HourlyAggregate;
import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import com.svenruppert.urlshortener.core.statistics.StatisticsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryStatisticsStoreTest {

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

  @Test
  void recordEvent_shouldStoreEventAndUpdateAggregates() {
    Instant eventTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0)
        .toInstant(ZoneOffset.UTC);
    RedirectEvent event = RedirectEvent.minimal("abc123", eventTime);

    store.recordEvent(event);

    assertEquals(1, store.getCountForDate("abc123", LocalDate.of(2024, 1, 15)));
    assertEquals(1, store.getTotalCount("abc123"));
  }

  @Test
  void recordEvent_shouldUpdateHourlyAggregate() {
    LocalDate date = LocalDate.of(2024, 1, 15);

    // Record events at different hours
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 10, 30, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 14, 0, 0).toInstant(ZoneOffset.UTC)));

    var hourly = store.getHourlyAggregate("abc123", date);
    assertTrue(hourly.isPresent());

    HourlyAggregate agg = hourly.get();
    assertEquals(2, agg.getCount(10));  // Two events at 10:xx
    assertEquals(1, agg.getCount(14));  // One event at 14:xx
    assertEquals(0, agg.getCount(12));  // No events at 12:xx
    assertEquals(3, agg.totalCount());
    assertEquals(10, agg.peakHour());   // Peak hour is 10
  }

  @Test
  void recordEvent_shouldUpdateDailyAggregate() {
    LocalDate date = LocalDate.of(2024, 1, 15);

    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 14, 0, 0).toInstant(ZoneOffset.UTC)));

    var daily = store.getDailyAggregate("abc123", date);
    assertTrue(daily.isPresent());

    DailyAggregate agg = daily.get();
    assertEquals(2, agg.totalCount());
    assertEquals(date, agg.date());
  }

  @Test
  void getCountForDateRange_shouldSumMultipleDays() {
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 14, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 14, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 16, 10, 0, 0).toInstant(ZoneOffset.UTC)));

    long count = store.getCountForDateRange("abc123",
                                            LocalDate.of(2024, 1, 14), LocalDate.of(2024, 1, 16));

    assertEquals(4, count);
  }

  @Test
  void getEventsForDate_shouldReturnEventsSortedByTimestamp() {
    Instant t1 = LocalDateTime.of(2024, 1, 15, 14, 0, 0).toInstant(ZoneOffset.UTC);
    Instant t2 = LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC);
    Instant t3 = LocalDateTime.of(2024, 1, 15, 12, 0, 0).toInstant(ZoneOffset.UTC);

    store.recordEvent(RedirectEvent.minimal("abc123", t1));
    store.recordEvent(RedirectEvent.minimal("abc123", t2));
    store.recordEvent(RedirectEvent.minimal("abc123", t3));

    List<RedirectEvent> events = store.getEventsForDate("abc123", LocalDate.of(2024, 1, 15));

    assertEquals(3, events.size());
    assertEquals(t2, events.get(0).timestamp()); // 10:00
    assertEquals(t3, events.get(1).timestamp()); // 12:00
    assertEquals(t1, events.get(2).timestamp()); // 14:00
  }

  @Test
  void getDailyAggregates_shouldReturnAggregatesInRange() {
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 13, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 14, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 16, 10, 0, 0).toInstant(ZoneOffset.UTC)));

    List<DailyAggregate> aggregates = store.getDailyAggregates("abc123",
                                                               LocalDate.of(2024, 1, 14), LocalDate.of(2024, 1, 15));

    assertEquals(2, aggregates.size());
    assertEquals(LocalDate.of(2024, 1, 14), aggregates.get(0).date());
    assertEquals(LocalDate.of(2024, 1, 15), aggregates.get(1).date());
  }

  @Test
  void isInHotWindow_shouldReturnTrueForRecentDates() {
    // Default hot window is 7 days
    // Clock is fixed at 2024-01-15

    assertTrue(store.isInHotWindow(LocalDate.of(2024, 1, 15)));   // Today
    assertTrue(store.isInHotWindow(LocalDate.of(2024, 1, 10)));   // 5 days ago
    assertTrue(store.isInHotWindow(LocalDate.of(2024, 1, 8)));    // 7 days ago (boundary)
    assertFalse(store.isInHotWindow(LocalDate.of(2024, 1, 7)));   // 8 days ago (outside)
    assertFalse(store.isInHotWindow(LocalDate.of(2024, 1, 1)));   // 14 days ago
  }

  @Test
  void getHourlyAggregate_shouldReturnEmptyForDatesOutsideHotWindow() {
    // Record event 10 days ago (outside default 7-day hot window)
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 5, 10, 0, 0).toInstant(ZoneOffset.UTC)));

    var hourly = store.getHourlyAggregate("abc123", LocalDate.of(2024, 1, 5));
    assertTrue(hourly.isEmpty());
  }

  @Test
  void removeAllForShortCode_shouldRemoveAllData() {
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("xyz789",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));

    assertEquals(1, store.getTotalCount("abc123"));
    assertEquals(1, store.getTotalCount("xyz789"));

    store.removeAllForShortCode("abc123");

    assertEquals(0, store.getTotalCount("abc123"));
    assertEquals(1, store.getTotalCount("xyz789"));
  }

  @Test
  void updateConfig_shouldChangeHotWindow() {
    StatisticsConfig newConfig = new StatisticsConfig();
    newConfig.setHotWindowDays(3);

    store.updateConfig(newConfig);

    assertEquals(3, store.getConfig().hotWindowDays());

    // With 3-day window, 2024-01-12 (3 days ago) should be in window
    // but 2024-01-11 (4 days ago) should not
    assertTrue(store.isInHotWindow(LocalDate.of(2024, 1, 12)));
    assertFalse(store.isInHotWindow(LocalDate.of(2024, 1, 11)));
  }

  @Test
  void recordEvent_shouldNotStoreWhenDisabled() {
    StatisticsConfig config = new StatisticsConfig();
    config.setStatisticsEnabled(false);
    store.updateConfig(config);

    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));

    assertEquals(0, store.getTotalCount("abc123"));
  }

  @Test
  void clear_shouldRemoveAllData() {
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("xyz789",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));

    assertEquals(2, store.getShortCodeCount());

    store.clear();

    assertEquals(0, store.getShortCodeCount());
    assertEquals(0, store.getTotalEventCount());
  }

  @Test
  void getAvailableDates_shouldReturnSortedDates() {
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 13, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("abc123",
                                            LocalDateTime.of(2024, 1, 14, 10, 0, 0).toInstant(ZoneOffset.UTC)));

    List<LocalDate> dates = store.getAvailableDates("abc123");

    assertEquals(3, dates.size());
    assertEquals(LocalDate.of(2024, 1, 13), dates.get(0));
    assertEquals(LocalDate.of(2024, 1, 14), dates.get(1));
    assertEquals(LocalDate.of(2024, 1, 15), dates.get(2));
  }

  @Test
  void multipleShortCodes_shouldBeIsolated() {
    store.recordEvent(RedirectEvent.minimal("code1",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("code1",
                                            LocalDateTime.of(2024, 1, 15, 11, 0, 0).toInstant(ZoneOffset.UTC)));
    store.recordEvent(RedirectEvent.minimal("code2",
                                            LocalDateTime.of(2024, 1, 15, 10, 0, 0).toInstant(ZoneOffset.UTC)));

    assertEquals(2, store.getTotalCount("code1"));
    assertEquals(1, store.getTotalCount("code2"));
    assertEquals(0, store.getTotalCount("code3"));
  }
}