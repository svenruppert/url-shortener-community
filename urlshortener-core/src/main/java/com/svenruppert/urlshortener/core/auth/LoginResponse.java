package com.svenruppert.urlshortener.core.auth;

/** Response of {@code POST /api/login} — the Bearer token plus display name. */
public record LoginResponse(String token, String user) {
}
