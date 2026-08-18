package junit.com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.ShortenerServer;
import com.svenruppert.urlshortener.client.AuditClient;
import com.svenruppert.urlshortener.client.AuthClient;
import com.svenruppert.urlshortener.client.UsersClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.svenruppert.urlshortener.core.DefaultValues.ADMIN_SERVER_HOST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage-G server backbone against the REAL in-process server (no mocks):
 * admin user CRUD + role mutation (with drift bump) + self-service
 * password change + audit query. Uses the seeded in-memory directory
 * (admin/admin) like the other client tests.
 */
class UsersAdminIT
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
  void adminListsSeededUsers() {
    var result = new UsersClient(adminUrl).list();
    assertTrue(result.isSuccess(), () -> "list failed: " + result);
    var names = result.getOrThrow().stream().map(u -> u.name()).toList();
    assertTrue(names.contains("Administrator"), () -> "expected admin in " + names);
  }

  @Test
  void createAssignRevokeDeleteUser() {
    var users = new UsersClient(adminUrl);

    var created = users.create("alice", "alice-strong-2026", List.of("USER"));
    assertTrue(created.isSuccess(), () -> "create failed: " + created);
    long id = created.getOrThrow().id();
    assertEquals(List.of("USER"), created.getOrThrow().roles());

    var promoted = users.assignRole(id, "ADMIN");
    assertTrue(promoted.isSuccess(), () -> "assign failed: " + promoted);
    assertTrue(promoted.getOrThrow().roles().contains("ADMIN"));

    var demoted = users.revokeRole(id, "ADMIN");
    assertTrue(demoted.isSuccess(), () -> "revoke failed: " + demoted);
    assertFalse(demoted.getOrThrow().roles().contains("ADMIN"));

    var deleted = users.delete(id);
    assertTrue(deleted.isSuccess(), () -> "delete failed: " + deleted);
  }

  @Test
  void createRejectsCompromisedPassword() {
    var result = new UsersClient(adminUrl).create("bob", "welcome1", List.of("USER"));
    assertTrue(result.isFailure(), "compromised password must be rejected");
  }

  @Test
  void lastAdminCannotBeDeleted() {
    var users = new UsersClient(adminUrl);
    long adminId = users.list().getOrThrow().stream()
        .filter(u -> u.roles().contains("ADMIN"))
        .findFirst().orElseThrow().id();
    var deleted = users.delete(adminId);
    assertTrue(deleted.isFailure(), "the last administrator must be protected");
  }

  @Test
  void selfServicePasswordChange() {
    // create a user, log in as them, change their own password, re-login
    var users = new UsersClient(adminUrl);
    var created = users.create("carol", "carol-initial-2026", List.of("USER"));
    assertTrue(created.isSuccess(), () -> "create failed: " + created);

    AuthClient auth = new AuthClient(adminUrl);
    String token = auth.login("carol", "carol-initial-2026").getOrThrow().token();

    var changed = auth.changePassword(token, "carol-initial-2026", "carol-rotated-2026");
    assertTrue(changed.isSuccess(), () -> "password change failed: " + changed);

    assertTrue(auth.login("carol", "carol-rotated-2026").isSuccess(), "new password must work");
    assertTrue(auth.login("carol", "carol-initial-2026").isFailure(), "old password must fail");
  }

  @Test
  void auditLogContainsEntries() {
    // creating a user emits a UserCreated audit event
    new UsersClient(adminUrl).create("dave", "dave-strong-2026", List.of("USER"));
    var audit = new AuditClient(adminUrl).fetch(null, 500);
    assertTrue(audit.isSuccess(), () -> "audit fetch failed: " + audit);
    List<?> entries = audit.getOrThrow();
    assertFalse(entries.isEmpty(), "audit log should contain at least the login/create events");
  }

  @Test
  void roleChangeDriftsExistingToken() {
    // carol logs in; admin revokes her USER role; her token must drift → 401
    var users = new UsersClient(adminUrl);
    long id = users.create("erin", "erin-strong-2026", List.of("USER")).getOrThrow().id();

    AuthClient erinAuth = new AuthClient(adminUrl);
    String erinToken = erinAuth.login("erin", "erin-strong-2026").getOrThrow().token();
    assertTrue(erinAuth.me(erinToken).isSuccess(), "token valid before role change");

    users.assignRole(id, "ADMIN");   // any role mutation bumps the version

    assertTrue(erinAuth.me(erinToken).isFailure(), "token must drift after the role change");
  }
}
