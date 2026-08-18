package com.svenruppert.urlshortener.core.campaign;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A named group of short links (1:many — a link belongs to at most one campaign).
 * {@code from}/{@code to} are an optional default reporting window; {@code type}
 * classifies the vehicle (PDF, magazine, …). Persistable via EclipseStore.
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
