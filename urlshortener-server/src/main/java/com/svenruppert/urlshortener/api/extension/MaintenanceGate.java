package com.svenruppert.urlshortener.api.extension;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide maintenance gate for the ADMIN server (V00.18.00 P090): while
 * closed, every secured admin endpoint answers 503 so a plug-in can take a
 * write-quiescent multi-store snapshot. The request that closed the gate
 * keeps running (it already passed the filter); the public redirect server
 * is deliberately NOT gated. Always close in a try/finally.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.18.00
 */
public final class MaintenanceGate {

  private static final AtomicBoolean CLOSED = new AtomicBoolean(false);

  private MaintenanceGate() {
  }

  /** @return {@code true} when this call actually closed the open gate. */
  public static boolean close() {
    return CLOSED.compareAndSet(false, true);
  }

  public static void open() {
    CLOSED.set(false);
  }

  public static boolean isClosed() {
    return CLOSED.get();
  }
}
