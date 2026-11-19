package io.slixes.archlens.delivery.rest;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.runtime.ShutdownEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionTokenServiceTest {

  @Test
  void avoidsBuildTimeRandomFieldsInNativeImageHeap() {
    boolean hasStaticRandom =
        Arrays.stream(SessionTokenService.class.getDeclaredFields())
            .anyMatch((Field f) ->
                Modifier.isStatic(f.getModifiers()) && Random.class.isAssignableFrom(f.getType()));
    assertFalse(
        hasStaticRandom,
        "SessionTokenService must not hold a static Random/SecureRandom in the GraalVM image heap");

    SessionTokenService service = new SessionTokenService(true, null, "127.0.0.1", 8088);
    String first = service.generateToken();
    String second = service.generateToken();
    assertNotEquals(first, second, "repeated generateToken calls reuse lazy CSPRNG safely");
    assertTrue(second.length() >= 43);
  }

  @Test
  void usesConfiguredTokenAndComparesExactly() {
    SessionTokenService service = new SessionTokenService(true, "abc123", "127.0.0.1", 8088);
    assertTrue(service.isEnabled());
    assertEquals("abc123", service.getToken());
    assertTrue(service.matches("abc123"));
    assertFalse(service.matches("abc12"));
    assertFalse(service.matches("abc1234"));
    assertFalse(service.matches(""));
    assertFalse(service.matches(null));
    assertEquals("archlens_session_8088", service.cookieName());
  }

  @Test
  void generatesStrongRandomTokenWhenNotConfigured() {
    SessionTokenService a = new SessionTokenService(true, null, "127.0.0.1", 8088);
    SessionTokenService b = new SessionTokenService(true, "  ", "127.0.0.1", 8088);
    assertTrue(a.getToken().length() >= 43, "32 random bytes, base64url");
    assertEquals(a.getToken(), a.getToken(), "token is stable for the process lifetime");
    assertNotEquals(a.getToken(), b.getToken());
    assertTrue(a.getToken().matches("[A-Za-z0-9_-]+"));
  }

  @Test
  void classifiesLoopbackHosts() {
    assertTrue(SessionTokenService.isLoopbackHost("127.0.0.1"));
    assertTrue(SessionTokenService.isLoopbackHost("localhost"));
    assertTrue(SessionTokenService.isLoopbackHost("::1"));
    assertTrue(SessionTokenService.isLoopbackHost("[::1]"));
    assertFalse(SessionTokenService.isLoopbackHost("0.0.0.0"));
    assertFalse(SessionTokenService.isLoopbackHost("::"));
    assertFalse(SessionTokenService.isLoopbackHost("[::]"));
    assertFalse(SessionTokenService.isLoopbackHost("192.168.1.20"));
    assertFalse(SessionTokenService.isLoopbackHost(""));
    assertFalse(SessionTokenService.isLoopbackHost("   "));
    assertFalse(SessionTokenService.isLoopbackHost(null));
  }

  @Test
  void computesStableNonSecretTokenFingerprint() {
    SessionTokenService a = new SessionTokenService(true, "abc123", "127.0.0.1", 8088);
    SessionTokenService b = new SessionTokenService(true, "different", "127.0.0.1", 8088);
    String fp = a.tokenFingerprint();
    assertEquals(12, fp.length());
    assertTrue(fp.matches("[0-9a-f]{12}"));
    assertEquals(fp, a.tokenFingerprint());
    assertNotEquals(fp, b.tokenFingerprint());
    assertFalse(fp.contains("abc123"));
  }

  @Test
  void writesOwnerOnlySessionFileAndCleansUpOnShutdown(@TempDir Path tempDir) throws IOException {
    Path sessionDir = tempDir.resolve("nested/archlens");
    SessionTokenService service = new SessionTokenService(true, null, "::1", 9091, sessionDir);

    service.startLifecycle(false, false);

    Path expectedFile = sessionDir.resolve("session-9091.json");
    assertTrue(Files.exists(expectedFile), "session file should be created");
    String content = Files.readString(expectedFile);
    assertTrue(content.contains("\"port\":9091"));
    assertTrue(content.contains("\"token\":\"" + service.getToken() + "\""));

    if (Files.getFileAttributeView(sessionDir, PosixFileAttributeView.class) != null) {
      assertEquals(
          PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(sessionDir));
      assertEquals(
          PosixFilePermissions.fromString("rw-------"),
          Files.getPosixFilePermissions(expectedFile));
    }

    service.onStop(new ShutdownEvent());
    assertFalse(Files.exists(expectedFile), "onStop should delete own session file");
  }

  @Test
  void tightensExistingSessionDirPermissionsAndPreservesForeignSessionFile(@TempDir Path tempDir)
      throws IOException {
    Path sessionDir = Files.createDirectories(tempDir.resolve("existing"));
    boolean supportsPosix =
        Files.getFileAttributeView(sessionDir, PosixFileAttributeView.class) != null;
    if (supportsPosix) {
      Files.setPosixFilePermissions(sessionDir, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    SessionTokenService service =
        new SessionTokenService(true, "fixed-tok", "0.0.0.0", 9092, sessionDir);
    service.startLifecycle(true, false);

    if (supportsPosix) {
      assertEquals(
          Set.copyOf(PosixFilePermissions.fromString("rwx------")),
          Files.getPosixFilePermissions(sessionDir));
    }

    Path sessionFile = sessionDir.resolve("session-9092.json");
    assertTrue(Files.exists(sessionFile));

    // Overwrite session file with another process's token; onStop must leave it intact
    Files.writeString(sessionFile, "{\"port\":9092,\"token\":\"newer-token\"}\n");
    service.onStop(new ShutdownEvent());
    assertTrue(Files.exists(sessionFile), "onStop must not delete another instance's session");
  }

  @Test
  void skipsSessionFileWhenDisabledOrStdioPortAndSupportsExplicitLog(@TempDir Path tempDir) {
    Path disabledDir = tempDir.resolve("disabled");
    SessionTokenService disabled = new SessionTokenService(false, "", "0.0.0.0", 8088, disabledDir);
    disabled.startLifecycle(false, false);
    assertFalse(disabled.isEnabled());
    assertFalse(Files.exists(disabledDir));
    disabled.onStop(new ShutdownEvent());

    Path stdioDir = tempDir.resolve("stdio");
    SessionTokenService stdio = new SessionTokenService(true, null, "127.0.0.1", 0, stdioDir);
    stdio.startLifecycle(false, true);
    assertFalse(Files.exists(stdioDir));

    Path explicitDir = tempDir.resolve("explicit");
    SessionTokenService explicit =
        new SessionTokenService(true, null, "127.0.0.1", 9093, explicitDir);
    explicit.startLifecycle(false, true);
    assertTrue(Files.exists(explicitDir.resolve("session-9093.json")));
    explicit.onStop(new ShutdownEvent());
  }
}
