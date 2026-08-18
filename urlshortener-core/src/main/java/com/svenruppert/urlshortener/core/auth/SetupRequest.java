package com.svenruppert.urlshortener.core.auth;

/** Body of {@code POST /api/setup} — token-gated first-admin creation. */
public record SetupRequest(String token, String username, String password) {
}
