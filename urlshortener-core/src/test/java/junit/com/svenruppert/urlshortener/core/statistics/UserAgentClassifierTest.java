package junit.com.svenruppert.urlshortener.core.statistics;

import com.svenruppert.urlshortener.core.statistics.RedirectEvent;
import com.svenruppert.urlshortener.core.statistics.UserAgentClassifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAgentClassifierTest {

  @Test
  void humansAreNotBots() {
    assertFalse(UserAgentClassifier.isBot(
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile Safari/604.1"));
    assertFalse(UserAgentClassifier.isBot(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"));
    // missing/unknown UA stays human (conservative — avoid over-filtering)
    assertFalse(UserAgentClassifier.isBot(null));
    assertFalse(UserAgentClassifier.isBot("   "));
    // the JDK/test HTTP client must not be misread as a bot
    assertFalse(UserAgentClassifier.isBot("Java-http-client/26"));
  }

  @Test
  void crawlersPreviewsMonitorsScriptsAreBots() {
    assertTrue(UserAgentClassifier.isBot("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
    assertTrue(UserAgentClassifier.isBot("TelegramBot (like TwitterBot)"));
    assertTrue(UserAgentClassifier.isBot("WhatsApp/2.23"));
    assertTrue(UserAgentClassifier.isBot("facebookexternalhit/1.1"));
    assertTrue(UserAgentClassifier.isBot("Slackbot-LinkExpanding 1.0"));
    assertTrue(UserAgentClassifier.isBot("UptimeRobot/2.0"));
    assertTrue(UserAgentClassifier.isBot("curl/8.4.0"));
    assertTrue(UserAgentClassifier.isBot("python-requests/2.31"));
    assertTrue(UserAgentClassifier.isBot("GPTBot/1.0"));
  }

  @Test
  void redirectEvent_derivesBotFromUserAgent() {
    Instant ts = Instant.parse("2026-07-24T10:00:00Z");
    assertTrue(new RedirectEvent("abc", ts, "TelegramBot", null, null, null).bot());
    assertFalse(new RedirectEvent("abc", ts, "Mozilla/5.0 Chrome/124", null, null, null).bot());
    assertFalse(RedirectEvent.minimal("abc", ts).bot());
  }
}
