package com.svenruppert.urlshortener.core.campaign;

import java.time.LocalDate;

/** Request body for creating a campaign. */
public record CampaignCreateRequest(
    String name,
    String description,
    CampaignType type,
    LocalDate from,
    LocalDate to
) {
}
