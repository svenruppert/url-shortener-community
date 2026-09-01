package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.urlshortener.api.security.model.InMemoryUserDirectory;
import com.svenruppert.urlshortener.api.security.model.UserDirectoryProvider;
import com.svenruppert.urlshortener.client.AuthClient;
import com.svenruppert.urlshortener.client.AuthContext;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_URL;

/**
 * Test helper: keeps the pre-seeded {@code admin/admin} + {@code user/user}
 * convention by installing a seeded in-memory user directory (so tests
 * bypass the token-based first-admin bootstrap of Stage E), then logs in
 * as admin and installs the Bearer token as the process-wide default for
 * the client wrappers (which read {@link AuthContext#getGlobalToken()}).
 */
final class TestAuth {

  private TestAuth() {
  }

  /**
   * Installs the seeded in-memory directory BEFORE the first
   * {@code ShortenerServer.init()} so the bootstrap flow sees an
   * existing admin and never blocks with 503. Lazy supplier — built
   * only after jCustos bootstrap configured the hashing service.
   */
  static void useSeededInMemoryDirectory() {
    UserDirectoryProvider.setDirectorySupplier(InMemoryUserDirectory::new);
  }

  /** Login against the default admin URL (fixed port). */
  static void loginAsAdmin() {
    loginAsAdmin(ADMIN_SERVER_URL);
  }

  /** Login against a specific admin URL (dynamic port tests). */
  static void loginAsAdmin(String adminBaseUrl) {
    var result = new AuthClient(adminBaseUrl).loginAndActivate("admin", "admin");
    if (result.isFailure()) {
      throw new IllegalStateException("test admin login failed: " + result);
    }
  }

  static void clear() {
    AuthContext.clear();
  }
}
