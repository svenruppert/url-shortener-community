package com.svenruppert.urlshortener.api.security.events;

import eu.jsentinel.jcustos.events.api.EventMetadata;
import eu.jsentinel.jcustos.events.api.EventType;
import eu.jsentinel.jcustos.events.api.JCustosEvent;
import eu.jsentinel.jcustos.events.api.JCustosEventCategory;

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
    implements JCustosEvent {

  /** The single event type published by this application onto the bus. */
  public static final EventType TYPE = EventType.of("urlshortener.security");

  @Override
  public EventType eventType() {
    return TYPE;
  }

  @Override
  public JCustosEventCategory category() {
    return JCustosEventCategory.AUTHENTICATION;
  }
}
