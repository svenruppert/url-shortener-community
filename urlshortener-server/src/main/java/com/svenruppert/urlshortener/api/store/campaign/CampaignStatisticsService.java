package com.svenruppert.urlshortener.api.store.campaign;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsReader;
import com.svenruppert.urlshortener.core.campaign.CampaignStatisticsResponse;
import com.svenruppert.urlshortener.core.campaign.CampaignStatisticsResponse.DayCount;
import com.svenruppert.urlshortener.core.campaign.CampaignStatisticsResponse.LinkStat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Rolls the per-link (human-only) daily aggregates up to a campaign or a tag:
 * grand total, a merged timeline and a per-link placement ranking with shares.
 * Pure fold over {@link StatisticsReader#getCountForDate}, so it needs no raw
 * events and inherits the bot exclusion baked into the aggregates.
 */
public final class CampaignStatisticsService
    implements HasLogger {

  private final CampaignStore campaigns;
  private final StatisticsReader statistics;

  public CampaignStatisticsService(CampaignStore campaigns, StatisticsReader statistics) {
    this.campaigns = campaigns;
    this.statistics = statistics;
  }

  public CampaignStatisticsResponse forCampaign(String campaignId, LocalDate from, LocalDate to) {
    return rollup("campaign:" + campaignId, campaigns.linksOfCampaign(campaignId), from, to);
  }

  public CampaignStatisticsResponse forTag(String tag, LocalDate from, LocalDate to) {
    return rollup("tag:" + CampaignStore.normalizeTag(tag), campaigns.linksOfTag(tag), from, to);
  }

  private CampaignStatisticsResponse rollup(String scope, Set<String> codes, LocalDate from, LocalDate to) {
    TreeMap<LocalDate, Long> byDate = new TreeMap<>();
    List<LinkStat> perLink = new ArrayList<>();
    long total = 0;

    for (String code : codes) {
      long codeTotal = 0;
      for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
        long c = statistics.getCountForDate(code, d);
        if (c > 0) {
          byDate.merge(d, c, Long::sum);
          codeTotal += c;
        }
      }
      total += codeTotal;
      perLink.add(new LinkStat(code, campaigns.labelOfLink(code).orElse(null), codeTotal, 0.0));
    }

    long grand = total;
    List<LinkStat> ranked = perLink.stream()
        .map(ls -> new LinkStat(ls.shortCode(), ls.label(), ls.count(),
            grand == 0 ? 0.0 : (double) ls.count() / grand))
        .sorted(Comparator.comparingLong(LinkStat::count).reversed())
        .toList();

    List<DayCount> timeline = byDate.entrySet().stream()
        .map(e -> new DayCount(e.getKey(), e.getValue()))
        .toList();

    return new CampaignStatisticsResponse(scope, from, to, total, timeline, ranked);
  }
}
