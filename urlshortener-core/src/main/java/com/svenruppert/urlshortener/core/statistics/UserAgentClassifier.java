package com.svenruppert.urlshortener.core.statistics;

import java.util.List;
import java.util.Locale;

/**
 * Classifies a redirect's {@code User-Agent} as bot vs. human. Short links —
 * especially QR/print/campaign links shared into chats — get hammered by
 * link-preview unfurlers, uptime monitors, security scanners and scripted
 * clients; counting them would skew every campaign metric. Only positively
 * recognized bot markers classify as a bot (a missing/unknown UA stays human,
 * to avoid over-filtering real traffic).
 *
 * <p>Deliberately does <b>not</b> match the generic JDK/HTTP client UAs
 * ({@code Java-http-client}, {@code okhttp}, …) so in-process test traffic and
 * the SDK are not misread as bots.
 */
public final class UserAgentClassifier {

  private UserAgentClassifier() {
  }

  /** Lowercased substrings that mark non-human traffic. */
  private static final List<String> BOT_MARKERS = List.of(
      "bot", "crawler", "spider", "crawl", "slurp",
      "facebookexternalhit", "facebot", "whatsapp", "telegram", "slackbot",
      "discordbot", "twitterbot", "linkedinbot", "pinterest", "redditbot",
      "embedly", "skypeuripreview", "vkshare", "preview",
      "monitor", "pingdom", "uptimerobot", "statuscake", "site24x7",
      "headlesschrome", "phantomjs", "google-inspectiontool", "chrome-lighthouse",
      "python-requests", "python-urllib", "curl/", "wget/", "libwww-perl",
      "scrapy", "masscan", "nmap", "zgrab",
      "ahrefs", "semrush", "mj12", "dotbot", "bytespider", "petalbot",
      "dataforseo", "gptbot", "ccbot", "claudebot", "perplexitybot"
  );

  /** {@code true} if the User-Agent looks like a bot/crawler/preview/monitor/scanner/script. */
  public static boolean isBot(String userAgent) {
    if (userAgent == null || userAgent.isBlank()) {
      return false;
    }
    String ua = userAgent.toLowerCase(Locale.ROOT);
    for (String marker : BOT_MARKERS) {
      if (ua.contains(marker)) {
        return true;
      }
    }
    return false;
  }
}
