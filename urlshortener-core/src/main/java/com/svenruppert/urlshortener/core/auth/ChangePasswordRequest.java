package com.svenruppert.urlshortener.core.auth;

/** Body of {@code POST /api/me/password}. */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
