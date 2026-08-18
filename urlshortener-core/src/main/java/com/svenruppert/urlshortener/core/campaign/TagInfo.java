package com.svenruppert.urlshortener.core.campaign;

/**
 * A distinct tag and how many links currently carry it. Returned by the tag
 * management listing ({@code GET /api/tags}).
 */
public record TagInfo(String tag, int linkCount) {
}
