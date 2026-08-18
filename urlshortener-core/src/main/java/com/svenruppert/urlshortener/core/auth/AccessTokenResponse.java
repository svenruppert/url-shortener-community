package com.svenruppert.urlshortener.core.auth;

/** Response of {@code POST /api/refresh} — a fresh short-lived access token. */
public record AccessTokenResponse(String token) {
}
