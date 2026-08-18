package com.svenruppert.urlshortener.core.auth;

/** Response of {@code GET /api/bootstrap/status}. */
public record BootstrapStatusResponse(boolean bootstrapRequired) {
}
