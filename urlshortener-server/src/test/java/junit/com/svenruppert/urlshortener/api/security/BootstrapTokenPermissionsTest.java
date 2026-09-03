package junit.com.svenruppert.urlshortener.api.security;

import com.svenruppert.urlshortener.api.security.bootstrap.BootstrapWiring;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.nio.file.attribute.PosixFileAttributeView;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SEC-L7 regression lock (V00.17.00 P080): the bootstrap token file — a
 * one-time admin credential — is forced to {@code rw-------} after the
 * bootstrap writes it; group/other access would let any local account take
 * over the instance before the first admin is created.
 */
class BootstrapTokenPermissionsTest {

  @Test
  void tokenFileIsRestrictedToTheOwner(@TempDir Path dir)
      throws Exception {
    Path token = dir.resolve("bootstrap.token");
    Files.writeString(token, "one-time-token");
    Files.setPosixFilePermissions(token, EnumSet.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));
    assumeTrue(Files.getFileAttributeView(token,
        PosixFileAttributeView.class) != null,
        "POSIX permissions unsupported on this file system");

    BootstrapWiring.enforceOwnerOnlyPermissions(token);

    assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(token),
        "the token file must be rw------- after enforcement");
  }

  @Test
  void missingFileIsANoop(@TempDir Path dir) {
    assertDoesNotThrow(() ->
        BootstrapWiring.enforceOwnerOnlyPermissions(dir.resolve("does-not-exist")));
  }
}
