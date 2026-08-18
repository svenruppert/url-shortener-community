package junit.com.svenruppert.urlshortener.api.store.statistics;

import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryStatisticsStore;
import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * No-mocks proof that bot traffic is stored raw but excluded from the human
 * aggregates: two human hits + one bot hit on the same code/day count as 2.
 */
class BotExclusionTest {

  private static final Instant TS =
      LocalDateTime.of(2026, 7, 24, 10, 0).toInstant(ZoneOffset.UTC);
  private static final LocalDate DAY = LocalDate.of(2026, 7, 24);

  private static RedirectEvent human(String code) {
    return new RedirectEvent(code, TS, "Mozilla/5.0 (Macintosh) Chrome/124 Safari/537.36", null, null, null);
  }

  private static RedirectEvent bot(String code) {
    return new RedirectEvent(code, TS, "TelegramBot (like TwitterBot)", null, null, null);
  }

  @Test
  void botsExcludedFromHumanAggregates() {
    var store = new InMemoryStatisticsStore(Clock.fixed(TS, ZoneOffset.UTC));
    store.recordEvent(human("abc"));
    store.recordEvent(bot("abc"));   // preview bot — must not count
    store.recordEvent(human("abc"));

    assertEquals(2, store.getCountForDate("abc", DAY), "only the two human hits count");
    assertEquals(2, store.getTotalCount("abc"));
  }

  @Test
  void reaggregateStaysHumanOnly() {
    var store = new InMemoryStatisticsStore(Clock.fixed(TS, ZoneOffset.UTC));
    store.recordEvent(human("xyz"));
    store.recordEvent(bot("xyz"));
    store.recordEvent(bot("xyz"));

    store.reaggregate(DAY, DAY); // rebuild aggregates from raw events

    assertEquals(1, store.getCountForDate("xyz", DAY), "reaggregate must exclude bots too");
  }
}
