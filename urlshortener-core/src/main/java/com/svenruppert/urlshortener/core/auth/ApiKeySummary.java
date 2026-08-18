package com.svenruppert.urlshortener.core.auth;

/**
 * One entry of {@code GET /api/me/api-keys}. Never carries the key itself —
 * only its id, label and creation timestamp (ISO-8601 string).
 */
public record ApiKeySummary(String id, String label, String createdAt) {
}
