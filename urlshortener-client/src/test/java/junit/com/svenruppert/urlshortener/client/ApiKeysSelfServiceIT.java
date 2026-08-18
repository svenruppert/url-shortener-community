package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.ApiKeysClient;
import com.svenruppert.urlshortener.core.auth.ApiKeySummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Self-service API-key management against the REAL in-process server (no mocks):
 * create → the plaintext {@code usk_…} is returned once → it appears in the
 * listing (without key material) → revoke removes it. Uses the seeded in-memory
 * directory (admin/admin) like the other client ITs.
 */
class ApiKeysSelfServiceIT
    implements HasLogger {

  private ShortenerServer server;
  private String adminUrl;

  @BeforeEach
  void startServer()
      throws IOException {
    TestAuth.useSeededInMemoryDirectory();
    server = new ShortenerServer();
    server.init(ADMIN_SERVER_HOST, 0);
    adminUrl = "http://" + ADMIN_SERVER_HOST + ":" + server.getPortAdmin();
    TestAuth.loginAsAdmin(adminUrl);
  }

  @AfterEach
  void stopServer() {
    server.shutdown();
    TestAuth.clear();
  }

  @Test
  void create_list_revoke_roundtrip() {
    var client = new ApiKeysClient(adminUrl);

    assertTrue(client.list().getOrThrow().isEmpty(), "no keys before the first create");

    var created = client.create("ci-key");
    assertTrue(created.isSuccess(), () -> "create failed: " + created);
    assertTrue(created.getOrThrow().key().startsWith("usk_"), "plaintext key returned once");
    assertEquals("ci-key", created.getOrThrow().label());

    List<ApiKeySummary> list = client.list().getOrThrow();
    assertEquals(1, list.size(), () -> "exactly one key expected: " + list);
    ApiKeySummary summary = list.getFirst();
    assertEquals("ci-key", summary.label());
    assertNotNull(summary.id(), "listing carries the key id");
    assertNotNull(summary.createdAt(), "listing carries the creation timestamp");

    var revoked = client.revoke(summary.id());
    assertTrue(revoked.isSuccess(), () -> "revoke failed: " + revoked);
    assertTrue(client.list().getOrThrow().isEmpty(), "listing empty after revoke");
  }

  @Test
  void revoke_unknownId_isFailure() {
    var revoked = new ApiKeysClient(adminUrl).revoke("does-not-exist");
    assertTrue(revoked.isFailure(), "revoking an unknown key id must not report success");
  }
}
