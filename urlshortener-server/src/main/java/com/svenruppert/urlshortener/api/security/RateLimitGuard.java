package com.svenruppert.urlshortener.api.security;

import com.sun.net.httpserver.HttpExchange;
import com.svenruppert.jsentinel.audit.NoopJSentinelAuditService;
import com.svenruppert.jsentinel.authorization.api.tenant.TenantId;
import com.svenruppert.jsentinel.ratelimiting.InMemoryRateLimitPolicy;
import com.svenruppert.jsentinel.ratelimiting.InMemoryRateLimitStore;
import com.svenruppert.jsentinel.ratelimiting.RateLimitDecision;
import com.svenruppert.jsentinel.ratelimiting.RateLimitKey;
import com.svenruppert.jsentinel.ratelimiting.RateLimitPolicy;
import com.svenruppert.urlshortener.core.SecurityProfile;

import java.time.Duration;

/**
 * Per-IP request rate limiting (P120). Backed by jSentinel's in-memory
 * {@link RateLimitPolicy}. Profile-aware (see {@link SecurityProfile}):
 * <ul>
 *   <li>PRODUCTION — 300 requests / minute / scope</li>
 *   <li>STRICT — 120 requests / minute / scope</li>
 *   <li>DEVELOPMENT — disabled (so local runs and tests are unaffected)</li>
 * </ul>
 * Consulted at the top of {@code SecurityHttpHandler} (scope {@code api:<ip>})
 * and {@code RedirectHandler} (scope {@code redirect:<ip>}).
 */
public final class RateLimitGuard {

  public static final RateLimitGuard INSTANCE = fromProfile();

  private final RateLimitPolicy policy; // null == disabled

  /** Active guard with an explicit limit/window (also used by tests). */
  public RateLimitGuard(int limit, Duration window) {
    this.policy = new InMemoryRateLimitPolicy(
        new InMemoryRateLimitStore(), NoopJSentinelAuditService.INSTANCE, limit, window);
  }

  private RateLimitGuard() {
    this.policy = null;
  }

  public boolean enabled() {
    return policy != null;
  }

  /** @return {@code true} if within the limit; {@code false} if it must be throttled. */
  public boolean allow(String scope) {
    if (policy == null) return true;
    RateLimitDecision d = policy.tryAcquire(new RateLimitKey(TenantId.DEFAULT, scope));
    return d instanceof RateLimitDecision.Allowed;
  }

  /** Client IP for scoping, honouring a single X-Forwarded-For hop. */
  public static String clientIp(HttpExchange ex) {
    String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
    var remote = ex.getRemoteAddress();
    return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
  }

  private static RateLimitGuard fromProfile() {
    return switch (SecurityProfile.current()) {
      case PRODUCTION -> new RateLimitGuard(300, Duration.ofMinutes(1));
      case STRICT -> new RateLimitGuard(120, Duration.ofMinutes(1));
      case DEVELOPMENT -> new RateLimitGuard();
    };
  }
}
