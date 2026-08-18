package junit.com.svenruppert.urlshortener.api.security.events;

import com.svenruppert.jsentinel.events.api.JSentinelEventSeverity;
import com.svenruppert.jsentinel.events.api.SignedJSentinelEventEnvelope;
import com.svenruppert.jsentinel.events.store.JSentinelEventCursor;
import com.svenruppert.jsentinel.events.store.StoredEnvelope;
import com.svenruppert.jsentinel.events.wire.EnvelopeWireCodec;
import com.svenruppert.urlshortener.api.security.events.SecurityEventBus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the V00.80.00 migration of {@code EnvelopeWireCodec} from
 * {@code events.rest} to {@code events.wire}: a real signed envelope
 * survives an encode → decode round-trip byte-identically. No mocks — the
 * envelope comes from the actual {@link SecurityEventBus}.
 *
 * <p>Prepared for the V00.81 removal of the deprecated {@code events.rest}
 * codec delegator.
 */
class EnvelopeWireCodecRoundTripTest {

  @Test
  void encodeDecodeIsLossless() {
    SecurityEventBus bus = SecurityEventBus.instance();
    bus.publishSecurity("wire.roundtrip", "tester", "codec migration", JSentinelEventSeverity.INFO);
    List<StoredEnvelope> all =
        bus.envelopeStore().findAfter(JSentinelEventCursor.start(), 100_000);
    SignedJSentinelEventEnvelope original = all.get(all.size() - 1).envelope();

    EnvelopeWireCodec codec = new EnvelopeWireCodec();
    SignedJSentinelEventEnvelope decoded = codec.decode(codec.encode(original)).getOrThrow();

    assertEquals(original.envelopeId(), decoded.envelopeId(), "envelope id preserved");
    assertEquals(original.eventId(), decoded.eventId(), "event id preserved");
    assertEquals(original.eventType(), decoded.eventType(), "event type preserved");
    assertArrayEquals(original.canonicalPayload(), decoded.canonicalPayload(),
        "canonical payload byte-identical");
    assertArrayEquals(original.signature(), decoded.signature(), "signature byte-identical");
  }
}
