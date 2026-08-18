package com.svenruppert.urlshortener.api.security;

import com.svenruppert.jsentinel.dx.runtime.JSentinelRuntime;

/**
 * Holds the {@link JSentinelRuntime} produced by the security bootstrap so the
 * admin health endpoint can report the wiring state (P250). Set once at startup.
 */
public final class SecurityDiagnostics {

  private static volatile JSentinelRuntime runtime;

  private SecurityDiagnostics() {
  }

  public static void set(JSentinelRuntime r) {
    runtime = r;
  }

  public static JSentinelRuntime runtime() {
    return runtime;
  }
}
