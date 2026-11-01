package com.svenruppert.urlshortener.api.security.storage;

import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.persistence.eclipsestore.StorageLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * One-time startup migration for the jCustos storage-directory rename.
 *
 * <p>Before the jCustos rebrand the framework store (audit trail, sessions,
 * credential records) lived in {@code <baseDir>/jsentinel-store}; jCustos now
 * opens it under {@link StorageLayout#frameworkSubdir()}
 * ({@code jcustos-store}). A data set written in the jSentinel era therefore
 * has an orphaned {@code jsentinel-store} that jCustos silently ignores,
 * creating a fresh, empty framework store next to it — the audit trail
 * restarts at genesis and the credential records (including the API-key
 * mapping the MCP service authenticates with) are no longer reachable, with
 * no error, warning or log entry.
 *
 * <p>The framework records were serialized under the old
 * {@code com.svenruppert.jsentinel.*} type names; jCustos reads
 * {@code eu.jsentinel.jcustos.*} and deliberately ships no legacy mapping
 * across the package rebrand, so the old binary store cannot be adopted. This
 * migration therefore does not try to carry the data over. It makes the
 * otherwise-silent transition loud and leaves a clean directory behind: it
 * logs a clear warning and renames the orphaned {@code jsentinel-store} aside
 * to {@code jsentinel-store.retired-<UTC timestamp>}. The raw store is
 * preserved in the operator's data backup.
 *
 * <p>Idempotent and best-effort: it acts only while a plain
 * {@code jsentinel-store} directory is present and never fails startup — a
 * rename error is logged and swallowed. This is intentionally a temporary shim
 * for the single existing installation and may be removed once that
 * installation has migrated.
 */
public final class LegacyFrameworkStoreMigration {

  /** Framework sub-directory name used before the jCustos rebrand. */
  static final String LEGACY_FRAMEWORK_SUBDIR = "jsentinel-store";

  private static final DateTimeFormatter RETIRED_STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  /** What the migration did on a given base directory. */
  public enum Outcome {
    /** No pre-jCustos framework store was present — nothing to do. */
    NO_LEGACY_STORE,
    /** An orphaned store was found and archived aside. */
    RETIRED,
    /** An orphaned store was found but could not be renamed (left in place). */
    RETIRE_FAILED
  }

  /**
   * @param outcome   what happened
   * @param retiredTo where the orphaned store was moved, or {@code null}
   */
  public record Result(Outcome outcome, Path retiredTo) {
  }

  private LegacyFrameworkStoreMigration() {
  }

  /**
   * Retires an orphaned pre-jCustos framework store under {@code baseDir}, if
   * one is present, logging a warning that describes what does not carry over.
   * Never throws — storage bootstrap must not fail on this convenience step.
   */
  public static Result retireOrphanedLegacyStore(Path baseDir) {
    Path legacy = baseDir.resolve(LEGACY_FRAMEWORK_SUBDIR);
    if (!Files.isDirectory(legacy)) {
      return new Result(Outcome.NO_LEGACY_STORE, null);
    }

    String current = StorageLayout.DEFAULT.frameworkSubdir();
    Path retiredTo = baseDir.resolve(
        LEGACY_FRAMEWORK_SUBDIR + ".retired-" + RETIRED_STAMP.format(Instant.now()));

    HasLogger.staticLogger().warn(
        "jCustos storage migration: found an orphaned pre-jCustos framework store at '{}'. "
            + "jCustos opens its framework store under '{}', so the old store is NOT read: the "
            + "audit trail restarts empty and the jCustos credential records (including the "
            + "API-key mapping the MCP service authenticates with) do not carry over. Users and "
            + "links are unaffected (stored elsewhere). The raw store is kept in the data backup; "
            + "re-issue the MCP API key after this start. Archiving it aside to '{}'.",
        legacy.toAbsolutePath(), current, retiredTo.getFileName());

    try {
      Files.move(legacy, retiredTo, StandardCopyOption.ATOMIC_MOVE);
      return new Result(Outcome.RETIRED, retiredTo);
    } catch (IOException atomicUnsupported) {
      try {
        Files.move(legacy, retiredTo);
        return new Result(Outcome.RETIRED, retiredTo);
      } catch (IOException moveFailed) {
        HasLogger.staticLogger().warn(
            "Could not archive the orphaned framework store '{}' aside — leaving it in place; "
                + "it is ignored by jCustos and safe to remove manually. Cause: {}",
            legacy.toAbsolutePath(), moveFailed.toString());
        return new Result(Outcome.RETIRE_FAILED, null);
      }
    }
  }
}
