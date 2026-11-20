package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspacePathResolverTest {

  @Test
  void testNormalizeRootDefaults() {
    assertEquals(".", WorkspacePathResolver.normalizeRoot(null));
    assertEquals(".", WorkspacePathResolver.normalizeRoot(""));
    assertEquals(".", WorkspacePathResolver.normalizeRoot("   "));
  }

  @Test
  void testNormalizeRootHomeExpansion() {
    String userHome = System.getProperty("user.home", "");
    String result = WorkspacePathResolver.normalizeRoot("~/myproject");
    assertEquals(userHome + "/myproject", result);
  }

  @Test
  void testNormalizeRootDoesNotRewriteArbitraryRepoNames() {
    assertEquals("foo/other-app/bar", WorkspacePathResolver.normalizeRoot("foo/other-app/bar"));
    assertEquals(
        "/workspace/labs/sample-service",
        WorkspacePathResolver.normalizeRoot("/workspace/labs/sample-service"));
  }

  @Test
  void testNormalizeRootContainerWorkspace(@TempDir Path tempDir) {
    Path fakeWorkspace = tempDir.resolve("workspace");
    fakeWorkspace.toFile().mkdirs();
    System.setProperty("archlens.container.workspace", fakeWorkspace.toString());
    try {
      String userHome = System.getProperty("user.home", "/Users/developer");
      String result = WorkspacePathResolver.normalizeRoot(userHome + "/workspace/acme/web-ui");
      assertEquals(fakeWorkspace.toString() + "/acme/web-ui", result);

      String result2 = WorkspacePathResolver.normalizeRoot("/Users/someone/workspace/other/proj");
      assertEquals(fakeWorkspace.toString() + "/other/proj", result2);

      String linuxResult =
          WorkspacePathResolver.normalizeRoot("/home/alice/workspace/team/service");
      assertEquals(fakeWorkspace.toString() + "/team/service", linuxResult);

      String exactRootResult = WorkspacePathResolver.normalizeRoot("/Users/someone/workspace");
      assertEquals(fakeWorkspace.toString(), exactRootResult);

      // Nested 'workspace' folder must NOT be truncated by endsWith("/workspace")
      assertEquals(
          fakeWorkspace.toString() + "/monorepo/packages/workspace",
          WorkspacePathResolver.normalizeRoot(
              "/Users/someone/workspace/monorepo/packages/workspace"));

      // Arbitrary-depth '/workspace/' outside /Users/<user>/workspace must NOT be rewritten
      assertEquals(
          "/Users/someone/Dropbox/client/workspace/api",
          WorkspacePathResolver.normalizeRoot("/Users/someone/Dropbox/client/workspace/api"));

      // Dot-dot traversal escaping /Users/<user>/workspace must NOT map into container mount
      assertEquals(
          "/Users/etc", WorkspacePathResolver.normalizeRoot("/Users/someone/workspace/../../etc"));
    } finally {
      System.clearProperty("archlens.container.workspace");
    }
  }

  @Test
  void testNormalizeRootExplicitHostWorkspace(@TempDir Path tempDir) {
    Path fakeWorkspace = tempDir.resolve("workspace");
    fakeWorkspace.toFile().mkdirs();
    System.setProperty("archlens.container.workspace", fakeWorkspace.toString());
    System.setProperty("archlens.host.workspace", "/opt/repos///");
    try {
      assertEquals(
          fakeWorkspace.toString() + "/billing/api",
          WorkspacePathResolver.normalizeRoot("/opt/repos/billing/api"));
      assertEquals(fakeWorkspace.toString(), WorkspacePathResolver.normalizeRoot("/opt/repos"));
      assertEquals(fakeWorkspace.toString(), WorkspacePathResolver.normalizeRoot("/opt/repos/"));
      // Sibling prefix must NOT be falsely rewritten
      assertEquals(
          "/opt/repos-other/billing",
          WorkspacePathResolver.normalizeRoot("/opt/repos-other/billing"));
      // Dot-dot traversal escaping /opt/repos must NOT map into container mount
      assertEquals("/opt/other", WorkspacePathResolver.normalizeRoot("/opt/repos/../other"));
    } finally {
      System.clearProperty("archlens.host.workspace");
      System.clearProperty("archlens.container.workspace");
    }
  }

  @Test
  void testNormalizeRootExplicitHostSubprojectDoesNotFallThroughToHomeWorkspace(
      @TempDir Path tempDir) {
    Path fakeWorkspace = tempDir.resolve("workspace");
    fakeWorkspace.toFile().mkdirs();
    System.setProperty("archlens.container.workspace", fakeWorkspace.toString());
    System.setProperty("archlens.host.workspace", "/Users/alice/workspace/labs/demo-app");
    try {
      assertEquals(
          fakeWorkspace.toString() + "/src",
          WorkspacePathResolver.normalizeRoot("/Users/alice/workspace/labs/demo-app/src"));
      assertEquals(
          "/Users/alice/workspace/labs/other-app",
          WorkspacePathResolver.normalizeRoot("/Users/alice/workspace/labs/other-app"));
    } finally {
      System.clearProperty("archlens.host.workspace");
      System.clearProperty("archlens.container.workspace");
    }
  }

  @Test
  void testIsIgnoredDirectory() {
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(null));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File(".git")));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File(".archlens")));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File("target")));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File("node_modules")));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File("build")));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File("dist")));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File("out")));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File("vendor")));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File("bin")));
    assertTrue(WorkspacePathResolver.isIgnoredDirectory(new File("obj")));
    assertFalse(WorkspacePathResolver.isIgnoredDirectory(new File("src")));
    assertFalse(WorkspacePathResolver.isIgnoredDirectory(new File("my-project")));
  }

  @Test
  void testIsProjectDirectory(@TempDir Path tempDir) throws IOException {
    assertFalse(WorkspacePathResolver.isProjectDirectory(null));
    assertFalse(WorkspacePathResolver.isProjectDirectory(tempDir.toFile()));

    Path mavenDir = tempDir.resolve("maven");
    Files.createDirectories(mavenDir);
    Files.writeString(mavenDir.resolve("pom.xml"), "<project></project>");
    assertTrue(WorkspacePathResolver.isProjectDirectory(mavenDir.toFile()));

    Path goDir = tempDir.resolve("go");
    Files.createDirectories(goDir);
    Files.writeString(goDir.resolve("go.mod"), "module foo");
    assertTrue(WorkspacePathResolver.isProjectDirectory(goDir.toFile()));

    Path cargoDir = tempDir.resolve("rust");
    Files.createDirectories(cargoDir);
    Files.writeString(cargoDir.resolve("Cargo.toml"), "[package]");
    assertTrue(WorkspacePathResolver.isProjectDirectory(cargoDir.toFile()));
  }

  @Test
  void testScanDirectoryForProjects(@TempDir Path tempDir) throws IOException {
    Path projectA = tempDir.resolve("repoA");
    Files.createDirectories(projectA);
    Files.writeString(projectA.resolve("pom.xml"), "<project></project>");

    Path ignored = tempDir.resolve("node_modules");
    Files.createDirectories(ignored);
    Files.writeString(ignored.resolve("package.json"), "{}");

    List<Map<String, String>> discovered = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Set<String> visited = new HashSet<>();

    WorkspacePathResolver.scanDirectoryForProjects(
        tempDir.toFile(), "", discovered, seen, visited, 2);

    assertEquals(1, discovered.size());
    assertEquals("repoA", discovered.get(0).get("name"));
  }
}
