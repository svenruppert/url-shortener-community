package com.svenruppert.urlshortener.core.auth;

/** Body of {@code POST /api/login}. */
public record LoginRequest(String username, String password) {
}
