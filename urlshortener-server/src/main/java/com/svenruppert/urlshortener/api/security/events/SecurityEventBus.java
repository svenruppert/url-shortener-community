package com.svenruppert.urlshortener.api.security.events;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.authorization.api.tenant.TenantId;
import com.svenruppert.jsentinel.events.api.EventMetadata;
import com.svenruppert.jsentinel.events.api.EventProducerId;
import com.svenruppert.jsentinel.events.api.JSentinelEvent;
import com.svenruppert.jsentinel.events.api.JSentinelEventSeverity;
import com.svenruppert.jsentinel.events.api.PayloadHashAlgorithm;
import com.svenruppert.jsentinel.events.api.SignedJSentinelEventEnvelope;
import com.svenruppert.jsentinel.events.api.KeyId;
import com.svenruppert.jsentinel.events.bus.ConsumePipeline;
import com.svenruppert.jsentinel.events.bus.DefaultJSentinelEventBus;
import com.svenruppert.jsentinel.events.bus.JSentinelEventBus;
import com.svenruppert.jsentinel.events.bus.ListenerErrorStrategy;
import com.svenruppert.jsentinel.events.bus.PublishPipeline;
import com.svenruppert.jsentinel.events.codec.CanonicalJsonPayloadCodec;
import com.svenruppert.jsentinel.events.codec.RecordReflectionCanonicalizer;
import com.svenruppert.jsentinel.events.integration.AuditEventBusListener;
import com.svenruppert.jsentinel.events.keys.InMemoryKeyManagement;
import com.svenruppert.jsentinel.events.producer.AllowListProducerPolicy;
import com.svenruppert.jsentinel.events.producer.JSentinelEventProducerPolicy;
import com.svenruppert.jsentinel.events.replay.InMemoryReplayStore;
import com.svenruppert.jsentinel.events.wire.EnvelopeWireCodec;
import com.svenruppert.jsentinel.events.rest.SseEventBroadcaster;
import com.svenruppert.jsentinel.events.sequence.InMemorySequenceStore;
import com.svenruppert.jsentinel.events.sequence.SequenceValidator;
import com.svenruppert.jsentinel.events.sequence.SequenceViolationStrategy;
import com.svenruppert.jsentinel.events.signature.Ed25519SignatureAlgorithm;
import com.svenruppert.jsentinel.events.signature.SignatureAlgorithm;
import com.svenruppert.jsentinel.events.signature.SignatureAlgorithms;
import com.svenruppert.jsentinel.events.store.InMemoryEnvelopeStore;
import com.svenruppert.jsentinel.events.store.JSentinelEventCursor;
import com.svenruppert.jsentinel.events.store.JSentinelEventEnvelopeStore;
import com.svenruppert.jsentinel.events.store.StoredEnvelope;
import com.svenruppert.jsentinel.logout.SubjectId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Signed security-event bus (P280). Assembles a tamper-evident
 * {@link JSentinelEventBus}: every published {@link JSentinelEvent} is
 * canonicalised, hashed (SHA-256) and Ed25519-signed into a
 * {@link SignedJSentinelEventEnvelope} before it is appended to the envelope
 * store — so the audit trail cannot be altered without invalidating the
 * signature.
 *
 * <p>Two consumers are attached:
 * <ul>
 *   <li>the framework {@link AuditEventBusListener} mirrors every bus event
 *       into the existing {@code JSentinelAuditService} (coupled lazily on the
 *       first publish, i.e. after security bootstrap);</li>
 *   <li>a bridge listener drains newly-stored envelopes to the
 *       {@link SseEventBroadcaster} that backs the {@code /api/events/stream}
 *       SSE endpoint for SIEM integration.</li>
 * </ul>
 *
 * <p>Stores are in-memory; durable, tamper-evident persistence via
 * {@code jSentinel-events-persistence-eclipsestore} is a carry-over.
 */
public final class SecurityEventBus
    implements HasLogger {

  private static final EventProducerId PRODUCER = EventProducerId.of("urlshortener-server");

  private static final class Holder {
    static final SecurityEventBus INSTANCE = new SecurityEventBus();
  }

  public static SecurityEventBus instance() {
    return Holder.INSTANCE;
  }

  private final InMemoryKeyManagement keys;
  private final JSentinelEventEnvelopeStore envelopeStore;
  private final JSentinelEventBus bus;
  private final SseEventBroadcaster broadcaster;
  private final EnvelopeWireCodec wireCodec = new EnvelopeWireCodec();
  private final SignatureAlgorithms algorithms = SignatureAlgorithms.defaults();
  private final AtomicReference<JSentinelEventCursor> broadcastCursor =
      new AtomicReference<>(JSentinelEventCursor.start());
  private final AtomicBoolean auditCoupled = new AtomicBoolean(false);

  private SecurityEventBus() {
    SignatureAlgorithm alg = new Ed25519SignatureAlgorithm();
    this.keys = new InMemoryKeyManagement(alg, KeyId.of("urlshortener-events-1"));
    PublishPipeline pipeline = new PublishPipeline(
        keys,
        new RecordReflectionCanonicalizer(),
        new CanonicalJsonPayloadCodec(),
        PayloadHashAlgorithm.SHA_256,
        PRODUCER,
        new InMemorySequenceStore(),
        new InMemoryReplayStore(),
        producerPolicy(),
        Duration.ofHours(1),
        Instant::now);
    this.envelopeStore = new InMemoryEnvelopeStore();
    this.bus = new DefaultJSentinelEventBus(
        pipeline, envelopeStore, ListenerErrorStrategy.ISOLATE_AND_CONTINUE,
        Runnable::run, Instant::now);
    this.broadcaster = new SseEventBroadcaster(wireCodec);
    // Bridge: every published (and thus stored) envelope is streamed to SSE.
    bus.subscribe(JSentinelEvent.class, event -> drainToBroadcaster());
  }

  private static JSentinelEventProducerPolicy producerPolicy() {
    return AllowListProducerPolicy.builder()
        .allow(PRODUCER, AppSecurityEvent.TYPE)
        .build();
  }

  /**
   * Signs and stores {@code event}, mirrors it into the audit log and streams
   * it over SSE. The audit coupling is established on the first call so it runs
   * after security bootstrap (when {@code securityAuditService()} is ready).
   */
  public void publish(JSentinelEvent event) {
    ensureAuditCoupled();
    bus.publish(event);
  }

  /**
   * Convenience for the common case: build the {@link EventMetadata} and
   * publish an {@link AppSecurityEvent}. {@code subject} is the username (or
   * subject id); {@code severity} distinguishes routine from suspicious events.
   */
  public void publishSecurity(String action, String subject, String detail,
                              JSentinelEventSeverity severity) {
    EventMetadata metadata = EventMetadata.create(
        TenantId.DEFAULT, SubjectId.of(subject), Instant.now(), severity);
    publish(new AppSecurityEvent(metadata, action, detail));
  }

  private void ensureAuditCoupled() {
    if (auditCoupled.compareAndSet(false, true)) {
      try {
        new AuditEventBusListener(JSentinelServiceResolver.securityAuditService())
            .subscribeTo(bus);
        logger().info("security event bus coupled to audit service");
      } catch (RuntimeException e) {
        auditCoupled.set(false); // retry on the next publish
        logger().warn("audit coupling deferred - {}", e.toString());
      }
    }
  }

  private synchronized void drainToBroadcaster() {
    List<StoredEnvelope> pending = envelopeStore.findAfter(broadcastCursor.get(), 128);
    for (StoredEnvelope stored : pending) {
      broadcaster.broadcast(stored);
      broadcastCursor.set(stored.cursor());
    }
  }

  /**
   * Cryptographically verifies a stored envelope against the current
   * verification key. Uses fresh replay/sequence stores so verification never
   * collides with the publish-side sequence state — the result reflects only
   * signature/payload integrity. Returns {@code false} on any tampering.
   */
  public boolean verify(SignedJSentinelEventEnvelope envelope) {
    ConsumePipeline consume = new ConsumePipeline(
        keys, algorithms, new InMemoryReplayStore(), new InMemorySequenceStore(),
        new SequenceValidator(), SequenceViolationStrategy.ACCEPT_WITH_WARNING, producerPolicy());
    return consume.verify(envelope, Instant.now()).isValid();
  }

  public JSentinelEventBus bus() {
    return bus;
  }

  public JSentinelEventEnvelopeStore envelopeStore() {
    return envelopeStore;
  }

  public SseEventBroadcaster broadcaster() {
    return broadcaster;
  }

  public EnvelopeWireCodec wireCodec() {
    return wireCodec;
  }
}
