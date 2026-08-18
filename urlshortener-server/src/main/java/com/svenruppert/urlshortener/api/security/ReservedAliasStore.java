package com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.core.AliasPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks aliases explicitly reserved by an admin for future use (P160/P300,
 * commercial). Distinct from the route/asset baseline in {@link AliasPolicy};
 * only entries recorded here can be lifted again via unreserve.
 */
public final class ReservedAliasStore {

  public static final ReservedAliasStore INSTANCE = new ReservedAliasStore();

  private final ConcurrentHashMap<String, Instant> reserved = new ConcurrentHashMap<>();

  private ReservedAliasStore() {
  }

  /** @return true if newly added (false if already reserved here). */
  public boolean add(String alias) {
    return reserved.putIfAbsent(AliasPolicy.normalize(alias), Instant.now(Clock.systemUTC())) == null;
  }

  /** @return true if it was reserved here and is now removed. */
  public boolean remove(String alias) {
    return reserved.remove(AliasPolicy.normalize(alias)) != null;
  }

  public Map<String, Instant> all() {
    return Map.copyOf(reserved);
  }
}
