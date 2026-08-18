package junit.com.svenruppert.urlshortener.api.security.events;

import com.svenruppert.jsentinel.events.api.JSentinelEventSeverity;
import com.svenruppert.jsentinel.events.api.SignedJSentinelEventEnvelope;
import com.svenruppert.jsentinel.events.store.JSentinelEventCursor;
import com.svenruppert.jsentinel.events.store.StoredEnvelope;
import com.svenruppert.urlshortener.api.security.events.SecurityEventBus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P280: proves the security event bus produces tamper-evident,
 * Ed25519-signed envelopes. No mocks — real signing and verification.
 */
class SecurityEventBusTest {

  @Test
  void publishesSignedEnvelopeThatVerifies()
      throws Exception {
    SecurityEventBus bus = SecurityEventBus.instance();
    long before = bus.envelopeStore().count();

    bus.publishSecurity("login.success", "alice", "unit-test",
        JSentinelEventSeverity.NOTICE);

    assertEquals(before + 1, bus.envelopeStore().count(),
        "publish appends exactly one signed envelope");

    SignedJSentinelEventEnvelope genuine = latest(bus);
    assertTrue(bus.verify(genuine), "genuine Ed25519-signed envelope verifies");

    // Flip one byte of the signature — verification must now fail.
    SignedJSentinelEventEnvelope tampered = tamperSignature(genuine);
    assertFalse(bus.verify(tampered), "tampered envelope fails verification");
  }

  private static SignedJSentinelEventEnvelope latest(SecurityEventBus bus) {
    List<StoredEnvelope> all =
        bus.envelopeStore().findAfter(JSentinelEventCursor.start(), 100_000);
    return all.get(all.size() - 1).envelope();
  }

  /** Rebuilds the envelope record with a single mutated signature byte. */
  private static SignedJSentinelEventEnvelope tamperSignature(SignedJSentinelEventEnvelope env)
      throws Exception {
    RecordComponent[] comps = SignedJSentinelEventEnvelope.class.getRecordComponents();
    Class<?>[] types = new Class<?>[comps.length];
    Object[] values = new Object[comps.length];
    for (int i = 0; i < comps.length; i++) {
      types[i] = comps[i].getType();
      Object value = comps[i].getAccessor().invoke(env);
      if ("signature".equals(comps[i].getName()) && value instanceof byte[] sig) {
        byte[] copy = sig.clone();
        copy[0] ^= 0x01;
        value = copy;
      }
      values[i] = value;
    }
    return SignedJSentinelEventEnvelope.class
        .getDeclaredConstructor(types).newInstance(values);
  }
}
