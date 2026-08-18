package com.svenruppert.urlshortener.core.campaign;

import java.util.List;

/**
 * Typed view of a link's campaign metadata returned by {@code GET /api/link-meta/{code}}:
 * its campaign membership ({@code campaignId}, nullable), cross-cutting {@code tags}
 * (m2m, normalized) and its placement {@code label} (nullable).
 */
public record LinkMetaResponse(String shortCode, String campaignId, List<String> tags, String label) {
}
