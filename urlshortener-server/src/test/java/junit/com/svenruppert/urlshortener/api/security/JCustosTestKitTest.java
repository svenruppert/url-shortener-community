package junit.com.svenruppert.urlshortener.api.security;

import eu.jsentinel.jcustos.authorization.api.JCustosSubject;
import eu.jsentinel.jcustos.authorization.api.permissions.PermissionName;
import eu.jsentinel.jcustos.authorization.api.roles.RoleName;
import eu.jsentinel.jcustos.test.JCustosSubjects;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the jCustos test-kit (P260) is on the test classpath and usable
 * for building synthetic subjects — the foundation for exercising authorization
 * paths without hand-rolled stubs.
 */
class JCustosTestKitTest {

  @Test
  void kitBuildsSubjectWithRoles() {
    JCustosSubject subject = JCustosSubjects.withRoles("alice", "ADMIN", "USER");
    Set<String> roles = subject.roleNames().stream().map(RoleName::value).collect(Collectors.toSet());
    assertTrue(roles.contains("ADMIN") && roles.contains("USER"), "roles present on synthetic subject");
  }

  @Test
  void kitBuildsSubjectWithPermissions() {
    JCustosSubject subject = JCustosSubjects.withPermissions("bob", "listUsers", "listAudit");
    Set<String> perms = subject.permissionNames().stream().map(PermissionName::value).collect(Collectors.toSet());
    assertTrue(perms.contains("listUsers") && perms.contains("listAudit"), "permissions present");
  }
}
