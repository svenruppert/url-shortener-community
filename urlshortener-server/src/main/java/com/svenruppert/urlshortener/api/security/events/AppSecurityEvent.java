package com.svenruppert.urlshortener.api.security.events;

import com.svenruppert.jsentinel.events.api.EventMetadata;
import com.svenruppert.jsentinel.events.api.EventType;
import com.svenruppert.jsentinel.events.api.JSentinelEvent;
import com.svenruppert.jsentinel.events.api.JSentinelEventCategory;

/**
 * A security-relevant application event (P280) carried on the signed
 * {@link SecurityEventBus}. A single {@link EventType} keeps the producer
 * allow-list simple; the {@code action} field distinguishes the concrete
 * occurrence (e.g. {@code login.success}, {@code login.failure}).
 *
 * <p>Being a {@code record}, all components are canonicalised by the
 * framework {@code RecordReflectionCanonicalizer} into the signed envelope
 * payload, so any tampering with {@code action}/{@code detail} breaks the
 * Ed25519 signature.
 */
public record AppSecurityEvent(EventMetadata metadata, String action, String detail)
    implements JSentinelEvent {

  /** The single event type published by this application onto the bus. */
  public static final EventType TYPE = EventType.of("urlshortener.security");

  @Override
  public EventType eventType() {
    return TYPE;
  }

  @Override
  public JSentinelEventCategory category() {
    return JSentinelEventCategory.AUTHENTICATION;
  }
}
