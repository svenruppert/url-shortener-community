package junit.com.svenruppert.urlshortener.api.security.events;

import eu.jsentinel.jcustos.events.api.JCustosEventSeverity;
import eu.jsentinel.jcustos.events.api.SignedJCustosEventEnvelope;
import eu.jsentinel.jcustos.events.store.JCustosEventCursor;
import eu.jsentinel.jcustos.events.store.StoredEnvelope;
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
        JCustosEventSeverity.NOTICE);

    assertEquals(before + 1, bus.envelopeStore().count(),
        "publish appends exactly one signed envelope");

    SignedJCustosEventEnvelope genuine = latest(bus);
    assertTrue(bus.verify(genuine), "genuine Ed25519-signed envelope verifies");

    // Flip one byte of the signature — verification must now fail.
    SignedJCustosEventEnvelope tampered = tamperSignature(genuine);
    assertFalse(bus.verify(tampered), "tampered envelope fails verification");
  }

  private static SignedJCustosEventEnvelope latest(SecurityEventBus bus) {
    List<StoredEnvelope> all =
        bus.envelopeStore().findAfter(JCustosEventCursor.start(), 100_000);
    return all.get(all.size() - 1).envelope();
  }

  /** Rebuilds the envelope record with a single mutated signature byte. */
  private static SignedJCustosEventEnvelope tamperSignature(SignedJCustosEventEnvelope env)
      throws Exception {
    RecordComponent[] comps = SignedJCustosEventEnvelope.class.getRecordComponents();
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
    return SignedJCustosEventEnvelope.class
        .getDeclaredConstructor(types).newInstance(values);
  }
}
