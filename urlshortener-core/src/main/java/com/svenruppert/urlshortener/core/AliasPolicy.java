package com.svenruppert.urlshortener.core;


import com.svenruppert.dependencies.core.logger.HasLogger;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class AliasPolicy
    implements HasLogger {

  public static final int MIN = 3;
  public static final int MAX = 32;
  public static final String REGEX_ALLOWED = "^[A-Za-z0-9_-]+$";
  private static final Pattern ALLOWED = Pattern.compile(REGEX_ALLOWED);

  /**
   * Baseline reserved aliases: server API namespace + web assets + the
   * well-known application routes. Extended at runtime via {@link #reserve}
   * (the UI registers all Vaadin {@code @Route} segments dynamically), so a
   * short code can never shadow a real route/asset path.
   */
  private static final Set<String> DEFAULT_RESERVED = Set.of(
      "api", "vaadin", "step-up",
      "favicon.ico", "robots.txt", "sw.js", "manifest.webmanifest", "index",
      "about", "create", "delete", "edit", "list", "toggle-active",
      "login", "logout", "setup", "profile", "users", "audit",
      "overview", "statistics", "stats", "bulk", "youtube", "me");

  /** Effective reserved set (lower-cased); replaced atomically by {@link #reserve}. */
  private static volatile Set<String> reserved = Set.copyOf(DEFAULT_RESERVED);

  private AliasPolicy() {
  }

  /**
   * Adds aliases to the reserved set (case-insensitive, additive, idempotent).
   * Intended to be called once at startup with the host application's route
   * segments so RESERVED is validated dynamically against all paths.
   */
  public static void reserve(Collection<String> aliases) {
    if (aliases == null || aliases.isEmpty()) return;
    Set<String> merged = new LinkedHashSet<>(reserved);
    for (String a : aliases) {
      if (a != null && !a.isBlank()) merged.add(a.toLowerCase(Locale.ROOT));
    }
    reserved = Set.copyOf(merged);
    HasLogger.staticLogger().info("AliasPolicy.reserve - reserved set now has {} entries", reserved.size());
  }

  /**
   * Removes aliases from the reserved set (case-insensitive). Used to lift an
   * explicit reservation (P300); route/asset baseline entries are re-added at
   * startup, so callers should only unreserve aliases they previously reserved.
   */
  public static void unreserve(Collection<String> aliases) {
    if (aliases == null || aliases.isEmpty()) return;
    Set<String> merged = new LinkedHashSet<>(reserved);
    for (String a : aliases) {
      if (a != null) merged.remove(a.toLowerCase(Locale.ROOT));
    }
    reserved = Set.copyOf(merged);
  }

  /** Unmodifiable view of the currently reserved aliases (lower-cased). */
  public static Set<String> reserved() {
    return reserved;
  }

  public static Validation validate(String alias) {
    if (alias == null || alias.isBlank()) return Validation.fail(Reason.NULL_OR_BLANK);
    if (alias.length() < MIN) return Validation.fail(Reason.TOO_SHORT);
    if (alias.length() > MAX) return Validation.fail(Reason.TOO_LONG);
    if (!ALLOWED.matcher(alias).matches()) return Validation.fail(Reason.INVALID_CHARS);
    if (reserved.contains(alias.toLowerCase(Locale.ROOT))) return Validation.fail(Reason.RESERVED);
    return Validation.ok();
  }

  public static String normalize(String alias) {
    return alias.toLowerCase(Locale.ROOT);
  }

  public enum Reason {
    NULL_OR_BLANK("Alias must not be empty."),
    TOO_SHORT("Alias is too short (min " + MIN + ")."),
    TOO_LONG("Alias is too long (max " + MAX + ")."),
    INVALID_CHARS("Alias contains invalid characters (allowed: [A-Za-z0-9_-])."),
    RESERVED("Alias is reserved and cannot be used.");

    /** English fallback used when no i18n translation is available (client/CLI). */
    public final String defaultMessage;

    Reason(String msg) {
      this.defaultMessage = msg;
    }

    /** Stable i18n key, e.g. {@code alias.reason.reserved}. Resolved by the UI. */
    public String messageKey() {
      return "alias.reason." + name().toLowerCase(Locale.ROOT);
    }
  }

  public record Validation(boolean valid, Reason reason) {
    public static Validation ok() {
      return new Validation(true, null);
    }

    public static Validation fail(Reason r) {
      return new Validation(false, r);
    }

    public boolean failed() {
      return !valid;
    }
  }
}
