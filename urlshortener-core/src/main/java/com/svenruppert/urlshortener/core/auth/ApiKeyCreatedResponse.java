package com.svenruppert.urlshortener.core.auth;

/**
 * Response of {@code POST /api/me/api-keys}: the plaintext key ({@code usk_…}),
 * shown exactly once, plus its label.
 */
public record ApiKeyCreatedResponse(String key, String label) {
}
