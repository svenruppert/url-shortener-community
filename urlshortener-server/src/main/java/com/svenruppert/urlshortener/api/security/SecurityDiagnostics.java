package com.svenruppert.urlshortener.api.security;

import eu.jsentinel.jcustos.dx.runtime.JCustosRuntime;

/**
 * Holds the {@link JCustosRuntime} produced by the security bootstrap so the
 * admin health endpoint can report the wiring state (P250). Set once at startup.
 */
public final class SecurityDiagnostics {

  private static volatile JCustosRuntime runtime;

  private SecurityDiagnostics() {
  }

  public static void set(JCustosRuntime r) {
    runtime = r;
  }

  public static JCustosRuntime runtime() {
    return runtime;
  }
}
