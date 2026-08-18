package com.svenruppert.urlshortener.core.campaign;

import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated (bot-excluded) statistics over a group of links — a campaign or a
 * tag. {@code total} + {@code timeline} answer "how did it do overall"; the
 * {@code perLink} ranking (with placement label + share) answers "which
 * placement drove it".
 */
public record CampaignStatisticsResponse(
    String scope,
    LocalDate from,
    LocalDate to,
    long total,
    List<DayCount> timeline,
    List<LinkStat> perLink
) {

  /** One day of the aggregated timeline. */
  public record DayCount(LocalDate date, long count) {
  }

  /** One member link's contribution: redirect count, placement label and share of the total. */
  public record LinkStat(String shortCode, String label, long count, double share) {
  }
}
