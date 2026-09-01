package com.svenruppert.urlshortener.api.security.events;

import com.svenruppert.dependencies.core.logger.HasLogger;
import eu.jsentinel.jcustos.authorization.api.JCustosServiceResolver;
import eu.jsentinel.jcustos.authorization.api.tenant.TenantId;
import eu.jsentinel.jcustos.events.api.EventMetadata;
import eu.jsentinel.jcustos.events.api.EventProducerId;
import eu.jsentinel.jcustos.events.api.JCustosEvent;
import eu.jsentinel.jcustos.events.api.JCustosEventSeverity;
import eu.jsentinel.jcustos.events.api.PayloadHashAlgorithm;
import eu.jsentinel.jcustos.events.api.SignedJCustosEventEnvelope;
import eu.jsentinel.jcustos.events.api.KeyId;
import eu.jsentinel.jcustos.events.bus.ConsumePipeline;
import eu.jsentinel.jcustos.events.bus.DefaultJCustosEventBus;
import eu.jsentinel.jcustos.events.bus.JCustosEventBus;
import eu.jsentinel.jcustos.events.bus.ListenerErrorStrategy;
import eu.jsentinel.jcustos.events.bus.PublishPipeline;
import eu.jsentinel.jcustos.events.codec.CanonicalJsonPayloadCodec;
import eu.jsentinel.jcustos.events.codec.RecordReflectionCanonicalizer;
import eu.jsentinel.jcustos.events.integration.AuditEventBusListener;
import eu.jsentinel.jcustos.events.keys.InMemoryKeyManagement;
import eu.jsentinel.jcustos.events.producer.AllowListProducerPolicy;
import eu.jsentinel.jcustos.events.producer.JCustosEventProducerPolicy;
import eu.jsentinel.jcustos.events.replay.InMemoryReplayStore;
import eu.jsentinel.jcustos.events.wire.EnvelopeWireCodec;
import eu.jsentinel.jcustos.events.sequence.InMemorySequenceStore;
import eu.jsentinel.jcustos.events.sequence.SequenceValidator;
import eu.jsentinel.jcustos.events.sequence.SequenceViolationStrategy;
import eu.jsentinel.jcustos.events.signature.Ed25519SignatureAlgorithm;
import eu.jsentinel.jcustos.events.signature.SignatureAlgorithm;
import eu.jsentinel.jcustos.events.signature.SignatureAlgorithms;
import eu.jsentinel.jcustos.events.store.InMemoryEnvelopeStore;
import eu.jsentinel.jcustos.events.store.JCustosEventCursor;
import eu.jsentinel.jcustos.events.store.JCustosEventEnvelopeStore;
import eu.jsentinel.jcustos.events.store.StoredEnvelope;
import eu.jsentinel.jcustos.logout.SubjectId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Signed security-event bus (P280). Assembles a tamper-evident
 * {@link JCustosEventBus}: every published {@link JCustosEvent} is
 * canonicalised, hashed (SHA-256) and Ed25519-signed into a
 * {@link SignedJCustosEventEnvelope} before it is appended to the envelope
 * store — so the audit trail cannot be altered without invalidating the
 * signature.
 *
 * <p>Two consumers are attached:
 * <ul>
 *   <li>the framework {@link AuditEventBusListener} mirrors every bus event
 *       into the existing {@code JCustosAuditService} (coupled lazily on the
 *       first publish, i.e. after security bootstrap);</li>
 *   <li>a bridge listener drains newly-stored envelopes to every registered
 *       {@link #addEnvelopeListener(Consumer) envelope listener}.</li>
 * </ul>
 *
 * <p>The listener seam keeps outbound transports out of the community edition:
 * shipping envelopes to an SSE stream, a webhook or a SIEM collector is a
 * plug-in concern, registered at server start via
 * {@link #addEnvelopeListener(Consumer)}. Without a plug-in the bus still signs,
 * stores and audits every event — only nothing leaves the process.
 *
 * <p>Stores are in-memory; durable, tamper-evident persistence via
 * {@code jCustos-events-persistence-eclipsestore} is a carry-over.
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
  private final JCustosEventEnvelopeStore envelopeStore;
  private final JCustosEventBus bus;
  private final List<Consumer<StoredEnvelope>> envelopeListeners = new CopyOnWriteArrayList<>();
  private final EnvelopeWireCodec wireCodec = new EnvelopeWireCodec();
  private final SignatureAlgorithms algorithms = SignatureAlgorithms.defaults();
  private final AtomicReference<JCustosEventCursor> broadcastCursor =
      new AtomicReference<>(JCustosEventCursor.start());
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
    this.bus = new DefaultJCustosEventBus(
        pipeline, envelopeStore, ListenerErrorStrategy.ISOLATE_AND_CONTINUE,
        Runnable::run, Instant::now);
    // Bridge: every published (and thus stored) envelope reaches the listeners.
    bus.subscribe(JCustosEvent.class, event -> drainToListeners());
  }

  /**
   * Registers an outbound consumer for newly stored envelopes — the seam a
   * transport plug-in (SSE stream, webhook, SIEM collector) hooks into at
   * server start. Listeners see envelopes stored from now on; a listener that
   * throws is logged and skipped, it never breaks the bus or its peers.
   */
  public void addEnvelopeListener(Consumer<StoredEnvelope> listener) {
    envelopeListeners.add(java.util.Objects.requireNonNull(listener, "listener must not be null"));
  }

  private static JCustosEventProducerPolicy producerPolicy() {
    return AllowListProducerPolicy.builder()
        .allow(PRODUCER, AppSecurityEvent.TYPE)
        .build();
  }

  /**
   * Signs and stores {@code event}, mirrors it into the audit log and streams
   * it over SSE. The audit coupling is established on the first call so it runs
   * after security bootstrap (when {@code securityAuditService()} is ready).
   */
  public void publish(JCustosEvent event) {
    ensureAuditCoupled();
    bus.publish(event);
  }

  /**
   * Convenience for the common case: build the {@link EventMetadata} and
   * publish an {@link AppSecurityEvent}. {@code subject} is the username (or
   * subject id); {@code severity} distinguishes routine from suspicious events.
   */
  public void publishSecurity(String action, String subject, String detail,
                              JCustosEventSeverity severity) {
    EventMetadata metadata = EventMetadata.create(
        TenantId.DEFAULT, SubjectId.of(subject), Instant.now(), severity);
    publish(new AppSecurityEvent(metadata, action, detail));
  }

  private void ensureAuditCoupled() {
    if (auditCoupled.compareAndSet(false, true)) {
      try {
        new AuditEventBusListener(JCustosServiceResolver.securityAuditService())
            .subscribeTo(bus);
        logger().info("security event bus coupled to audit service");
      } catch (RuntimeException e) {
        auditCoupled.set(false); // retry on the next publish
        logger().warn("audit coupling deferred - {}", e.toString());
      }
    }
  }

  private synchronized void drainToListeners() {
    List<StoredEnvelope> pending = envelopeStore.findAfter(broadcastCursor.get(), 128);
    for (StoredEnvelope stored : pending) {
      for (Consumer<StoredEnvelope> listener : envelopeListeners) {
        try {
          listener.accept(stored);
        } catch (RuntimeException e) {
          logger().warn("envelope listener failed - {}", e.toString());
        }
      }
      broadcastCursor.set(stored.cursor());
    }
  }

  /**
   * Cryptographically verifies a stored envelope against the current
   * verification key. Uses fresh replay/sequence stores so verification never
   * collides with the publish-side sequence state — the result reflects only
   * signature/payload integrity. Returns {@code false} on any tampering.
   */
  public boolean verify(SignedJCustosEventEnvelope envelope) {
    ConsumePipeline consume = new ConsumePipeline(
        keys, algorithms, new InMemoryReplayStore(), new InMemorySequenceStore(),
        new SequenceValidator(), SequenceViolationStrategy.ACCEPT_WITH_WARNING, producerPolicy());
    return consume.verify(envelope, Instant.now()).isValid();
  }

  public JCustosEventBus bus() {
    return bus;
  }

  public JCustosEventEnvelopeStore envelopeStore() {
    return envelopeStore;
  }

  public EnvelopeWireCodec wireCodec() {
    return wireCodec;
  }
}
