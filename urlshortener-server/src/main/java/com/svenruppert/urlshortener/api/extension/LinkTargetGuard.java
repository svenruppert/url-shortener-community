package com.svenruppert.urlshortener.api.extension;

import java.util.Optional;

/**
 * Open-core SPI: a plug-in jar vetoes a short link's target URL — both when a
 * link is created and when it would be redirected. The community edition ships
 * no implementation (no guard registered = nothing is ever vetoed), so the base
 * behavior is unchanged; an enterprise plug-in (e.g. managed banned targets)
 * registers one via
 * {@code META-INF/services/com.svenruppert.urlshortener.api.extension.LinkTargetGuard}.
 *
 * <p>Experimental open-core SPI — shape may still change.
 *
 * @since 00.23.00
 */
public interface LinkTargetGuard {

  /**
   * @param targetUrl the link's target URL
   * @return a short human-readable reason when the target must be blocked,
   *         or {@link Optional#empty()} to allow it
   */
  Optional<String> veto(String targetUrl);
}
