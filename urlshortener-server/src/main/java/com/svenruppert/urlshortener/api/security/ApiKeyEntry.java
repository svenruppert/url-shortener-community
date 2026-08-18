package com.svenruppert.urlshortener.api.security;

import java.io.Serializable;
import java.time.Instant;

/**
 * A stored API key. The plaintext key is never kept — its SHA-256 hash is the
 * map key in the store; this record holds only the public projection
 * ({@code id}, owning {@code userId}, {@code label}, {@code createdAt}).
 * Persistable (EclipseStore) so keys survive a server restart.
 *
 * <p>{@code versionAtIssue} (SEC-L2, V00.17.00 P080) snapshots the subject's
 * security version at key-issue time so the role-drift kill switch covers API
 * keys like it covers tokens. {@code null} on entries persisted before this
 * field existed — those legacy keys are grandfathered (no drift check).
 */
public record ApiKeyEntry(String id, Long userId, String label, Instant createdAt, Long versionAtIssue)
    implements Serializable {
}
