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
  void testNormalizeRootAliases() {
    assertEquals("foo/archlens/bar", WorkspacePathResolver.normalizeRoot("foo/agentlens/bar"));
    assertEquals(
        "foo/archlens/bar", WorkspacePathResolver.normalizeRoot("foo/unclebob-design/bar"));
  }

  @Test
  void testNormalizeRootContainerWorkspace(@TempDir Path tempDir) {
    Path fakeWorkspace = tempDir.resolve("workspace");
    fakeWorkspace.toFile().mkdirs();
    System.setProperty("archlens.container.workspace", fakeWorkspace.toString());
    try {
      String userHome = System.getProperty("user.home", "/Users/fady");
      String result = WorkspacePathResolver.normalizeRoot(userHome + "/workspace/khasis/khasis-ui");
      assertEquals(fakeWorkspace.toString() + "/khasis/khasis-ui", result);

      String result2 = WorkspacePathResolver.normalizeRoot("/Users/someone/workspace/other/proj");
      assertEquals(fakeWorkspace.toString() + "/other/proj", result2);
    } finally {
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
