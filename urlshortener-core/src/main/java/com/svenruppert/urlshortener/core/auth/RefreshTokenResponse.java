package com.svenruppert.urlshortener.core.auth;

/** Response of {@code POST /api/me/refresh-token} — a long-lived refresh token. */
public record RefreshTokenResponse(String refreshToken) {
}
