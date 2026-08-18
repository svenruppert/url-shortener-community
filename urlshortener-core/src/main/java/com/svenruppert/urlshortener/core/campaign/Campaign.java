package com.svenruppert.urlshortener.core.campaign;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A named group of short links (1:many — a link belongs to at most one campaign).
 * {@code from}/{@code to} are an optional default reporting window; {@code type}
 * classifies the vehicle (PDF, magazine, …). Persistable via EclipseStore.
 *
 * <p>Persistence-compatibility type (V00.18.00 P085, D4): the campaign FEATURE
 * lives in the enterprise link-meta plug-in, but this record stays in the
 * community edition under its original FQN so a pre-split {@code DataRoot}
 * with stored campaigns keeps loading — the data lies dormant without a
 * plug-in and re-awakens with a licensed one. No community code uses it.
 */
public record Campaign(
    String id,
    String name,
    String description,
    CampaignType type,
    LocalDate from,
    LocalDate to,
    Long ownerUserId,
    Instant createdAt
) implements Serializable {
}
