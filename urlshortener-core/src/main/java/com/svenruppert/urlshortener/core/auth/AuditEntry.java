package com.svenruppert.urlshortener.core.auth;

/** One audit-log row for {@code GET /api/audit}. */
public record AuditEntry(String timestamp, String type, String subject, String detail) {
}
