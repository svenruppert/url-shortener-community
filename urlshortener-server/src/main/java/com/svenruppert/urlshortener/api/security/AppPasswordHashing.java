package com.svenruppert.urlshortener.api.security;

import com.svenruppert.jsentinel.authentication.PasswordHasher;
import com.svenruppert.jsentinel.credential.password.CredentialVerificationResult;
import com.svenruppert.jsentinel.credential.password.PasswordHashingService;
import com.svenruppert.jsentinel.credential.password.RehashDecision;
import com.svenruppert.jsentinel.credential.password.bouncycastle.BouncyCastleHashingServices;

/**
 * Single application-wide password hashing service.
 *
 * <p>The first-admin bootstrap ({@code InitialAdminBootstrapService})
 * needs a {@link PasswordHashingService}; the user directories verify
 * logins through a {@link PasswordHasher}. jSentinel's resolver hands
 * out a <em>different</em> default {@code PasswordHasher}
 * ({@code Pbkdf2PasswordHasher}) than {@link PasswordHashingServices#defaults()},
 * and their hash strings do not cross-verify. This holder pins ONE
 * {@link PasswordHashingService} and exposes a {@link PasswordHasher}
 * adapter over it, so a password hashed at bootstrap verifies at login
 * with the identical algorithm and parameters.
 *
 * <p>Independent of the jSentinel bootstrap lifecycle — usable at any
 * time (in particular by the seeded {@code InMemoryUserDirectory} the
 * tests install before the server starts).
 *
 * <p>Stage F: the pinned service is Argon2id
 * ({@link BouncyCastleHashingServices#modern()}). Because it is the
 * single instance used by the directories, the bootstrap service and
 * the framework credential subsystem, switching it here migrates the
 * whole application to Argon2id with the cross-verify guarantee intact.
 */
public final class AppPasswordHashing {

  /** The one {@link PasswordHashingService} used everywhere (Argon2id). */
  public static final PasswordHashingService SERVICE = BouncyCastleHashingServices.modern();

  private static final PasswordHasher HASHER = new ServiceBackedHasher(SERVICE);

  private AppPasswordHashing() {
  }

  /** {@link PasswordHasher} view over {@link #SERVICE} for the user directories. */
  public static PasswordHasher hasher() {
    return HASHER;
  }

  /** A precomputed valid hash used for timing-safe dummy verification (P140). */
  private static final String DUMMY_HASH =
      SERVICE.hash("timing-safe-dummy-secret".toCharArray()).encodedHash();

  /**
   * Runs a verification against a fixed dummy hash so a login attempt for an
   * unknown user costs the same KDF time as for a known one (anti
   * user-enumeration, P140). The boolean result is intentionally discarded.
   */
  public static void dummyVerify(char[] password) {
    if (password == null) return;
    SERVICE.verify(password, DUMMY_HASH);
  }

  private record ServiceBackedHasher(PasswordHashingService service) implements PasswordHasher {

    @Override
    public String hash(char[] password) {
      return service.hash(password).encodedHash();
    }

    @Override
    public boolean verify(char[] password, String encodedHash) {
      return service.verify(password, encodedHash) instanceof CredentialVerificationResult.Verified;
    }

    @Override
    public boolean needsRehash(String encodedHash) {
      return service.needsRehash(encodedHash) instanceof RehashDecision.Required;
    }
  }
}
