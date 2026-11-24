package io.slixes.archlens.workspace.fs;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilesystemGatewayTest {

  @Test
  void listsProjectsDirectoriesAllowedRootsAndNativePickerBranches(@TempDir Path tempDir)
      throws IOException {
    LocalFilesystemGateway gateway = new LocalFilesystemGateway();

    Map<String, Object> projects = gateway.listProjects();
    assertTrue(projects.containsKey("current"));
    assertTrue(projects.containsKey("discovered"));

    assertFalse(LocalFilesystemGateway.isWithinAllowedRoots(null, List.of(tempDir)));
    assertFalse(LocalFilesystemGateway.isWithinAllowedRoots(tempDir, null));
    assertFalse(LocalFilesystemGateway.isWithinAllowedRoots(tempDir, List.of()));

    Set<Path> specRoots = new LinkedHashSet<>();
    LocalFilesystemGateway.addCanonicalDir(specRoots, null);
    LocalFilesystemGateway.addCanonicalDir(specRoots, tempDir.resolve("missing-root"));
    LocalFilesystemGateway.addRootsFromSpec(specRoots, null);
    LocalFilesystemGateway.addRootsFromSpec(specRoots, "   ");
    LocalFilesystemGateway.addRootsFromSpec(
        specRoots, tempDir + File.pathSeparator + " " + File.pathSeparator + tempDir);
    assertEquals(1, specRoots.size());
    assertFalse(gateway.resolveAllowedBrowseRoots(null).isEmpty());

    Path allowed = Files.createDirectories(tempDir.resolve("allowed"));
    Path mavenApp = Files.createDirectories(allowed.resolve("my-maven-app/src/main/java"));
    Files.writeString(
        mavenApp.getParent().getParent().getParent().resolve("pom.xml"), "<project/>");
    Path gradleKts = Files.createDirectories(allowed.resolve("my-kts-app"));
    Files.writeString(gradleKts.resolve("build.gradle.kts"), "plugins {}");
    Files.createDirectories(allowed.resolve("target"));
    Files.createDirectories(allowed.resolve("build"));
    Path sub = Files.createDirectories(allowed.resolve("docs-folder"));
    Path outside = Files.createDirectories(tempDir.resolve("outside"));

    Map<String, Object> denied = gateway.listDirectories(outside.toString(), List.of(allowed));
    assertEquals(Boolean.TRUE, denied.get("denied"));

    Map<String, Object> atRoot = gateway.listDirectories(allowed.toString(), List.of(allowed));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> dirs = (List<Map<String, Object>>) atRoot.get("directories");
    assertEquals(3, dirs.size());

    Map<String, Object> nested = gateway.listDirectories(sub.toString(), List.of(allowed));
    assertNotNull(nested.get("parentPath"));

    // Missing child directory inside allowed root falls back to existing parent
    Map<String, Object> missingChild =
        gateway.listDirectories(allowed.resolve("missing-sub").toString(), List.of(allowed));
    assertEquals(allowed.toRealPath().toString(), missingChild.get("currentPath"));

    // Deeply nested missing path inside allowed root falls back to first allowed root
    Map<String, Object> deepMissing =
        gateway.listDirectories(allowed.resolve("a/b/c").toString(), List.of(allowed));
    assertEquals(allowed.toRealPath().toString(), deepMissing.get("currentPath"));

    // Tilde expansion with ~/ subpath
    Map<String, Object> tildeSub = gateway.listDirectories("~/__nonexistent_archlens__", null);
    assertNotNull(tildeSub.get("currentPath"));

    // Native picker via injected NativePickerRunner on macOS
    String origOs = System.getProperty("os.name");
    String origHeadless = System.getProperty("java.awt.headless");
    try {
      System.setProperty("os.name", "Mac OS X");
      System.setProperty("java.awt.headless", "false");
      System.clearProperty("test.headless");
      System.clearProperty("archlens.container.workspace");

      LocalFilesystemGateway customPicker =
          new LocalFilesystemGateway(() -> Map.of("success", true, "path", "/tmp/picked"));
      assertEquals("/tmp/picked", customPicker.pickDirectory().get("path"));
    } finally {
      System.setProperty("os.name", origOs);
      if (origHeadless != null) {
        System.setProperty("java.awt.headless", origHeadless);
      } else {
        System.clearProperty("java.awt.headless");
      }
    }
  }

  @Test
  void readsConfinedSourceCodeAndRejectsEscapes(@TempDir Path tempDir) throws IOException {
    LocalFilesystemGateway gateway = new LocalFilesystemGateway();
    Path project = Files.createDirectories(tempDir.resolve("project"));
    Path src = Files.createDirectories(project.resolve("src"));
    Path validFile = Files.writeString(src.resolve("App.java"), "class App {}\n");
    Path secret = Files.writeString(tempDir.resolve("secret.env"), "SECRET=1");

    Map<String, Object> missingRoot =
        gateway.readConfinedSourceCode("App.java", 1, tempDir.resolve("missing-root").toString());
    assertEquals("Project root not found", missingRoot.get("error"));

    Map<String, Object> ok = gateway.readConfinedSourceCode("src/App.java", 3, project.toString());
    assertEquals("App.java", ok.get("fileName"));
    assertEquals(3, ok.get("targetLine"));
    assertTrue(((String) ok.get("content")).contains("class App"));

    Map<String, Object> absOk =
        gateway.readConfinedSourceCode(validFile.toString(), 1, project.toString());
    assertEquals("App.java", absOk.get("fileName"));

    Map<String, Object> missingFile =
        gateway.readConfinedSourceCode("Missing.java", 1, project.toString());
    assertTrue(((String) missingFile.get("error")).startsWith("File not found"));

    Map<String, Object> outside =
        gateway.readConfinedSourceCode(secret.toString(), 1, project.toString());
    assertEquals(Boolean.TRUE, outside.get("denied"));

    Map<String, Object> traversal =
        gateway.readConfinedSourceCode("../secret.env", 1, project.toString());
    assertEquals(Boolean.TRUE, traversal.get("denied"));

    try {
      Files.createSymbolicLink(project.resolve("Link.java"), secret);
      assertEquals(
          Boolean.TRUE,
          gateway.readConfinedSourceCode("Link.java", 1, project.toString()).get("denied"));
    } catch (UnsupportedOperationException | IOException ignored) {
      // Symlinks unsupported
    }
  }

  @Test
  void createsSnapshotRestoresFilesAndProtectsAgainstUnsafePathsAndSymlinks(@TempDir Path tempDir)
      throws IOException {
    LocalFilesystemGateway gateway = new LocalFilesystemGateway();
    Path project = Files.createDirectories(tempDir.resolve("project"));
    Path archlensDir = Files.createDirectories(project.resolve(".archlens"));
    Path policyPath = Files.writeString(archlensDir.resolve("policy.json"), "{\"title\":\"Base\"}");
    Path srcDir = Files.createDirectories(project.resolve("src/com/example"));
    Path serviceFile =
        Files.writeString(srcDir.resolve("OrderService.java"), "class OrderService {}");
    Files.writeString(srcDir.resolve("README.md"), "# ignored non-source");
    Files.createDirectories(project.resolve("node_modules/pkg"));

    String snapshotId = gateway.createPreRefactorSnapshot(project.toString());
    assertTrue(snapshotId.startsWith("snap-"));

    Files.writeString(policyPath, "{\"title\":\"Corrupted\"}");
    Files.writeString(serviceFile, "BROKEN");
    Path extraFile = Files.writeString(srcDir.resolve("Extra.java"), "class Extra {}");

    VerifyAndRollbackRefactorUseCase.RollbackResult restored =
        gateway.restoreSnapshot(project.toString(), snapshotId);
    assertTrue(restored.restored());
    assertEquals("{\"title\":\"Base\"}", Files.readString(policyPath));
    assertEquals("class OrderService {}", Files.readString(serviceFile));
    assertFalse(Files.exists(extraFile));
    assertEquals(List.of("src/com/example/Extra.java"), restored.trashedFiles());

    // Policy-only snapshot without manifest/files directory
    Path policyOnlySnap =
        Files.createDirectories(project.resolve(".archlens/snapshots/snap-9-abcdef01"));
    Files.writeString(policyOnlySnap.resolve("policy.json"), "{\"title\":\"PolicyOnly\"}");
    assertTrue(gateway.restoreSnapshot(project.toString(), "snap-9-abcdef01").restored());
    assertEquals("{\"title\":\"PolicyOnly\"}", Files.readString(policyPath));

    for (String bad : new String[] {"../../../fake", "snap-1-0a1b2c3d/../../../fake", "x"}) {
      assertThrows(
          IllegalArgumentException.class, () -> gateway.restoreSnapshot(project.toString(), bad));
    }
    assertFalse(gateway.restoreSnapshot(project.toString(), null).restored());
    assertFalse(gateway.restoreSnapshot(project.toString(), "   ").restored());
    assertFalse(gateway.restoreSnapshot(project.toString(), "snap-1-0a1b2c3d").restored());

    Path badSnap = Files.createDirectories(project.resolve(".archlens/snapshots/snap-2-0a1b2c3d"));
    Files.createDirectories(badSnap.resolve("files"));
    Files.writeString(badSnap.resolve("manifest.txt"), "\n../../outside.java\n");
    assertThrows(
        IllegalArgumentException.class,
        () -> gateway.restoreSnapshot(project.toString(), "snap-2-0a1b2c3d"));

    Path fake = Files.createDirectories(tempDir.resolve("fake/files"));
    Files.writeString(tempDir.resolve("fake/manifest.txt"), "");
    try {
      Files.createSymbolicLink(
          project.resolve(".archlens/snapshots/snap-3-0a1b2c3d"), fake.getParent());
      assertFalse(gateway.restoreSnapshot(project.toString(), "snap-3-0a1b2c3d").restored());

      Path outsideTrash = Files.createDirectories(tempDir.resolve("outside-trash"));
      String snap2 = gateway.createPreRefactorSnapshot(project.toString());
      Files.writeString(srcDir.resolve("NewWork.java"), "class NewWork {}");
      Path trashRoot = project.resolve(".archlens/trash");
      if (Files.exists(trashRoot)) {
        try (var walk = Files.walk(trashRoot)) {
          walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
      }
      Files.createSymbolicLink(trashRoot, outsideTrash);
      assertThrows(
          IllegalArgumentException.class, () -> gateway.restoreSnapshot(project.toString(), snap2));
      Files.deleteIfExists(trashRoot);

      // Post-snapshot symlinked parent directory under src/ must be rejected before Files.copy
      Path outsideVictimDir = Files.createDirectories(tempDir.resolve("outside-victim"));
      Path outsideVictimFile =
          Files.writeString(outsideVictimDir.resolve("OrderService.java"), "UNTOUCHED_OUTSIDE");
      try (var walk = Files.walk(srcDir)) {
        walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
      Files.createSymbolicLink(srcDir, outsideVictimDir);
      assertThrows(
          IllegalArgumentException.class, () -> gateway.restoreSnapshot(project.toString(), snap2));
      assertEquals("UNTOUCHED_OUTSIDE", Files.readString(outsideVictimFile));
    } catch (UnsupportedOperationException | IOException ignored) {
      // Symlinks unsupported
    }
  }
}
