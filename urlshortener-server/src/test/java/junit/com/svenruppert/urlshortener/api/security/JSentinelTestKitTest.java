package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.jsentinel.authorization.api.JSentinelSubject;
import com.svenruppert.jsentinel.authorization.api.permissions.PermissionName;
import com.svenruppert.jsentinel.authorization.api.roles.RoleName;
import com.svenruppert.jsentinel.test.JSentinelSubjects;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the jSentinel test-kit (P260) is on the test classpath and usable
 * for building synthetic subjects — the foundation for exercising authorization
 * paths without hand-rolled stubs.
 */
class JSentinelTestKitTest {

  @Test
  void kitBuildsSubjectWithRoles() {
    JSentinelSubject subject = JSentinelSubjects.withRoles("alice", "ADMIN", "USER");
    Set<String> roles = subject.roleNames().stream().map(RoleName::value).collect(Collectors.toSet());
    assertTrue(roles.contains("ADMIN") && roles.contains("USER"), "roles present on synthetic subject");
  }

  @Test
  void kitBuildsSubjectWithPermissions() {
    JSentinelSubject subject = JSentinelSubjects.withPermissions("bob", "listUsers", "listAudit");
    Set<String> perms = subject.permissionNames().stream().map(PermissionName::value).collect(Collectors.toSet());
    assertTrue(perms.contains("listUsers") && perms.contains("listAudit"), "permissions present");
  }
}
