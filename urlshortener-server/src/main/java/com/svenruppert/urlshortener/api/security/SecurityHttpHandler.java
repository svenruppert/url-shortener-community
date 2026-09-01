package com.svenruppert.urlshortener.api.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import eu.jsentinel.jcustos.authorization.annotations.RequiresAllPermissions;
import eu.jsentinel.jcustos.authorization.annotations.RequiresAnyPermission;
import eu.jsentinel.jcustos.authorization.annotations.RequiresPermission;
import eu.jsentinel.jcustos.authorization.api.JCustosSubject;
import eu.jsentinel.jcustos.authorization.api.permissions.PermissionName;
import com.svenruppert.urlshortener.api.security.bootstrap.BootstrapWiring;
import com.svenruppert.urlshortener.api.security.model.ApiUser;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.api.security.services.VersionBumper;
import com.svenruppert.urlshortener.api.utils.ErrorResponses;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.svenruppert.urlshortener.core.DefaultValues.AUTHORIZATION_HEADER;
import static com.svenruppert.urlshortener.core.DefaultValues.BEARER_PREFIX;

/**
 * Security adapter around protected handlers (PRE-REBUILD-SCOPE
 * Kap. 2.10): resolves the Bearer subject, enforces the permission(s)
 * declared on the delegate's class, and exposes the subject to the
 * delegate through {@link CurrentSubject}.
 *
 * <p>The required permissions are declared with the jCustos framework
 * annotations from {@code authorization.annotations} (P220):
 * {@link RequiresPermission @RequiresPermission} /
 * {@link RequiresAllPermissions @RequiresAllPermissions} (subject must hold
 * <em>all</em> listed) and {@link RequiresAnyPermission @RequiresAnyPermission}
 * (subject must hold <em>at least one</em>). The home-grown
 * {@link Authorize @Authorize} is still honoured as a deprecated bridge so the
 * migration stays evolutionary.
 *
 * <ul>
 *   <li>no/unknown token → 401</li>
 *   <li>missing permission → 403</li>
 *   <li>no permission annotation on the delegate → authenticated-only</li>
 * </ul>
 */
public final class SecurityHttpHandler
    implements HttpHandler, HasLogger {

  private final HttpHandler delegate;
  private final List<PermissionName> requiredAll;
  private final List<PermissionName> requiredAny;
  private final ApiRestSubjectResolver resolver = new ApiRestSubjectResolver();

  private SecurityHttpHandler(HttpHandler delegate) {
    this.delegate = delegate;
    Class<?> type = delegate.getClass();
    this.requiredAll = resolveAll(type);
    this.requiredAny = resolveAny(type);
  }

  /**
   * Permissions the subject must hold <em>all</em> of. Reads the framework
   * {@link RequiresPermission}/{@link RequiresAllPermissions}; falls back to the
   * deprecated home-grown {@link Authorize} so unmigrated handlers keep working.
   */
  @SuppressWarnings("deprecation")
  private static List<PermissionName> resolveAll(Class<?> type) {
    RequiresPermission rp = type.getAnnotation(RequiresPermission.class);
    if (rp != null) return toNames(rp.value());
    RequiresAllPermissions rap = type.getAnnotation(RequiresAllPermissions.class);
    if (rap != null) return toNames(rap.value());
    Authorize authorize = type.getAnnotation(Authorize.class);
    if (authorize != null) return toNames(new String[] {authorize.value()});
    return List.of();
  }

  /** Permissions of which the subject must hold <em>at least one</em>. */
  private static List<PermissionName> resolveAny(Class<?> type) {
    RequiresAnyPermission rap = type.getAnnotation(RequiresAnyPermission.class);
    return rap == null ? List.of() : toNames(rap.value());
  }

  private static List<PermissionName> toNames(String[] values) {
    return Arrays.stream(values)
        .filter(v -> v != null && !v.isBlank())
        .map(PermissionName::new)
        .toList();
  }

  public static SecurityHttpHandler secure(HttpHandler delegate) {
    return new SecurityHttpHandler(delegate);
  }

  @Override
  public void handle(HttpExchange exchange)
      throws IOException {
    // Per-IP rate limiting (P120); no-op unless a production/strict profile
    // is active.
    if (!RateLimitGuard.INSTANCE.allow("api:" + RateLimitGuard.clientIp(exchange))) {
      logger().info("rate limit exceeded (api) for {}", exchange.getRequestURI().getPath());
      ErrorResponses.withStatus(exchange, HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded");
      return;
    }
    // While no initial admin exists, every protected endpoint reports
    // 503 — the operator must POST /api/setup first (Kap. 2.8).
    if (BootstrapWiring.instance().stateService().bootstrapRequired()) {
      ErrorResponses.serviceUnavailable(exchange, "initial admin not yet created; POST /api/setup");
      return;
    }
    Optional<JCustosSubject> subject = resolver.resolveSubject(RestRequests.from(exchange));
    if (subject.isEmpty()) {
      subject = apiKeySubject(exchange); // P160: X-API-Key fallback
    }
    if (subject.isEmpty()) {
      logger().info("unauthenticated request to {}", exchange.getRequestURI().getPath());
      ErrorResponses.unauthorized(exchange);
      return;
    }
    // Drift detection: if the subject's version was bumped (role change)
    // after this token was issued, the token is stale — force re-login.
    if (isStale(exchange, subject.get())) {
      logger().info("stale token for subject '{}' — version drifted", subject.get().displayName());
      ErrorResponses.unauthorized(exchange);
      return;
    }
    PermissionName missing = firstMissing(subject.get());
    if (missing != null) {
      logger().info("subject '{}' lacks permission '{}' for {}",
          subject.get().displayName(), missing.value(),
          exchange.getRequestURI().getPath());
      ErrorResponses.forbidden(exchange, missing.value());
      return;
    }
    CurrentSubject.set(exchange, subject.get());
    delegate.handle(exchange);
  }

  /**
   * Returns the first declared permission the subject fails to satisfy, or
   * {@code null} when the subject is authorized. All {@link #requiredAll}
   * permissions must be present; if {@link #requiredAny} is non-empty the
   * subject must additionally hold at least one of them.
   */
  private PermissionName firstMissing(JCustosSubject subject) {
    var held = subject.permissionNames();
    for (PermissionName p : requiredAll) {
      if (!held.contains(p)) return p;
    }
    if (!requiredAny.isEmpty() && requiredAny.stream().noneMatch(held::contains)) {
      return requiredAny.get(0);
    }
    return null;
  }

  /**
   * Resolves a subject from an {@code X-API-Key} header (P160), for
   * non-interactive/programmatic access. Since SEC-L2 (V00.17.00 P080) the
   * key honors the same lifecycle rules as tokens: an optional maximum age
   * ({@code -Dapp.apikey.maxAgeDays}, 0 = unlimited) and the role-drift kill
   * switch via the version snapshot captured at issue time (legacy entries
   * without a snapshot are grandfathered).
   */
  private Optional<JCustosSubject> apiKeySubject(HttpExchange exchange) {
    String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
    if (apiKey == null || apiKey.isBlank()) {
      return Optional.empty();
    }
    Optional<ApiKeyEntry> entryOpt = ApiKeyStore.INSTANCE.resolveEntry(apiKey);
    if (entryOpt.isEmpty()) {
      return Optional.empty();
    }
    ApiKeyEntry entry = entryOpt.get();
    if (apiKeyExpired(entry)) {
      logger().info("expired api key '{}' rejected", entry.id());
      return Optional.empty();
    }
    Optional<ApiUser> user = UserDirectoryProvider.directory().findById(entry.userId());
    if (user.isEmpty()) {
      return Optional.empty();
    }
    if (entry.versionAtIssue() != null
        && VersionBumper.current(user.get()) != entry.versionAtIssue()) {
      logger().info("stale api key '{}' — version drifted since issue", entry.id());
      return Optional.empty();
    }
    return Optional.of(ApiRestSubjectResolver.toSubject(user.get()));
  }

  private static boolean apiKeyExpired(ApiKeyEntry entry) {
    long maxAgeDays = Long.getLong("app.apikey.maxAgeDays", 0L);
    if (maxAgeDays <= 0 || entry.createdAt() == null) {
      return false;
    }
    return entry.createdAt().plus(java.time.Duration.ofDays(maxAgeDays))
        .isBefore(java.time.Instant.now());
  }

  /**
   * Compares the version snapshot captured in the token at login with
   * the subject's current version. A mismatch means an admin bumped
   * the version (role change) after login — the token is stale.
   */
  private boolean isStale(HttpExchange exchange, JCustosSubject subject) {
    String auth = exchange.getRequestHeaders().getFirst(AUTHORIZATION_HEADER);
    if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
      return false;
    }
    String token = auth.substring(BEARER_PREFIX.length()).trim();
    Optional<Long> snapshot = TokenStore.INSTANCE.versionSnapshot(token);
    if (snapshot.isEmpty()) {
      return false;
    }
    Long id = parseId(subject.subjectId());
    if (id == null) {
      return false;
    }
    ApiUser user = UserDirectoryProvider.directory().findById(id).orElse(null);
    if (user == null) {
      return false;
    }
    return VersionBumper.current(user) != snapshot.get();
  }

  private static Long parseId(String subjectId) {
    try {
      return Long.valueOf(subjectId);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
