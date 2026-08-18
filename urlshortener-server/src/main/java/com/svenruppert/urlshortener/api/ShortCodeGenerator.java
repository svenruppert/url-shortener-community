package com.svenruppert.urlshortener.api;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates exactly 6-character Base-62 short codes.
 *
 * <p>An internal {@link AtomicLong} counter guarantees that every code is backed by a unique
 * counter value. The counter is then passed through a bijective 64-bit mixing function
 * (Murmur3 finaliser variant) before being encoded in Base-62, so consecutive counter values
 * produce very different codes and the output is not trivially guessable.
 *
 * <p>The code space has 62<sup>6</sup> = 56,800,235,584 distinct values, making systematic
 * enumeration impractical for realistic workloads.
 *
 * <p>The store layer is expected to retry on the rare occasion of a hash collision
 * (two counter values that map to the same final code after reduction modulo 62<sup>6</sup>).
 */
public final class ShortCodeGenerator {

  /** Base-62 alphabet: digits, uppercase, then lowercase. */
  private static final String CHARS =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private static final int CODE_LENGTH = 6;
  private static final long BASE       = CHARS.length();          // 62
  private static final long SPACE      = 56_800_235_584L;         // 62^6

  private final AtomicLong counter;

  public ShortCodeGenerator(long initialValue) {
    if (initialValue <= 99) {
      this.counter = new AtomicLong(100);
    } else {
      this.counter = new AtomicLong(initialValue);
    }
  }

  /** Returns a 6-character Base-62 code that is unique within the counter's lifetime. */
  public String nextCode() {
    final long id     = counter.getAndIncrement();
    final long mixed  = Math.floorMod(mixBits(id), SPACE);
    return encode(mixed);
  }

  /** Returns the current (next-to-be-used) counter value. */
  public long currentId() {
    return counter.get();
  }

  // ── Internal helpers ────────────────────────────────────────────────────────

  /**
   * Bijective 64-bit mixing function (Murmur3 finaliser variant).
   * Maps sequential inputs to a well-distributed pseudorandom output space.
   * The function is its own inverse over Z/2^64.
   */
  private static long mixBits(long x) {
    x ^= (x >>> 30);
    x *= 0xbf58476d1ce4e5b9L;
    x ^= (x >>> 27);
    x *= 0x94d049bb133111ebL;
    x ^= (x >>> 31);
    return x;
  }

  /**
   * Encodes {@code n} ∈ [0, SPACE) as exactly {@value CODE_LENGTH} Base-62 characters.
   */
  private static String encode(long n) {
    final char[] buf = new char[CODE_LENGTH];
    for (int i = CODE_LENGTH - 1; i >= 0; i--) {
      buf[i] = CHARS.charAt((int) (n % BASE));
      n /= BASE;
    }
    return new String(buf);
  }
}
