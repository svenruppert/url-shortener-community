package com.svenruppert.urlshortener.core.campaign;

/**
 * Result of a batch link assignment: how many links were assigned and how many
 * were skipped (blank codes or links the caller may not manage).
 */
public record BatchAssignResponse(int assigned, int skipped) {
}
