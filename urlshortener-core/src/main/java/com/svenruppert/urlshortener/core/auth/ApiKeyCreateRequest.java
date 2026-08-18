package com.svenruppert.urlshortener.core.auth;

/** Request body for {@code POST /api/me/api-keys}; {@code label} may be blank. */
public record ApiKeyCreateRequest(String label) {
}
