package com.design.umlviewer.resource;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemResourceTest {

  @Test
  void testListDirectoriesWithProjects(@TempDir Path tempDir) throws IOException {
    DiagramResource resource = new DiagramResource();

    // Create subdirectories
    Path mavenProject = tempDir.resolve("my-maven-app");
    Files.createDirectories(mavenProject.resolve("src/main/java"));
    Files.writeString(mavenProject.resolve("pom.xml"), "<project></project>");

    Path gradleProject = tempDir.resolve("my-gradle-app");
    Files.createDirectories(gradleProject);
    Files.writeString(gradleProject.resolve("build.gradle"), "// gradle");

    Path nodeProject = tempDir.resolve("my-node-app");
    Files.createDirectories(nodeProject);
    Files.writeString(nodeProject.resolve("package.json"), "{}");

    Path regularFolder = tempDir.resolve("regular-folder");
    Files.createDirectories(regularFolder);

    Path hiddenFolder = tempDir.resolve(".hidden");
    Files.createDirectories(hiddenFolder);

    Path ignoredFolder = tempDir.resolve("node_modules");
    Files.createDirectories(ignoredFolder);

    // Call listDirectories
    Map<String, Object> result = resource.listDirectories(tempDir.toString());
    assertNotNull(result);
    assertEquals(tempDir.toRealPath().toString(), result.get("currentPath"));
    assertNotNull(result.get("breadcrumbs"));
    assertNotNull(result.get("quickNav"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> directories = (List<Map<String, Object>>) result.get("directories");
    assertNotNull(directories);
    assertEquals(
        4, directories.size()); // maven, gradle, node, regular (hidden & node_modules excluded)

    // Check maven project entry
    Map<String, Object> mavenEntry =
        directories.stream()
            .filter(d -> "my-maven-app".equals(d.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals(Boolean.TRUE, mavenEntry.get("isProject"));
    assertEquals("Maven", mavenEntry.get("projectType"));

    // Check gradle project entry
    Map<String, Object> gradleEntry =
        directories.stream()
            .filter(d -> "my-gradle-app".equals(d.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals(Boolean.TRUE, gradleEntry.get("isProject"));
    assertEquals("Gradle", gradleEntry.get("projectType"));

    // Check node project entry
    Map<String, Object> nodeEntry =
        directories.stream()
            .filter(d -> "my-node-app".equals(d.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals(Boolean.TRUE, nodeEntry.get("isProject"));
    assertEquals("Node", nodeEntry.get("projectType"));

    // Check regular folder entry
    Map<String, Object> regularEntry =
        directories.stream()
            .filter(d -> "regular-folder".equals(d.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals(Boolean.FALSE, regularEntry.get("isProject"));
    assertNull(regularEntry.get("projectType"));
  }

  @Test
  void testListDirectoriesDefaultAndFallback() {
    DiagramResource resource = new DiagramResource();

    // Default path
    Map<String, Object> defaultResult = resource.listDirectories(null);
    assertNotNull(defaultResult);
    assertNotNull(defaultResult.get("currentPath"));
    assertNotNull(defaultResult.get("breadcrumbs"));

    // Blank path
    Map<String, Object> blankResult = resource.listDirectories("   ");
    assertNotNull(blankResult);
    assertNotNull(blankResult.get("currentPath"));

    // Non-existent path
    Map<String, Object> nonExistent =
        resource.listDirectories("/path/does/not/exist/at/all/hopefully/12345");
    assertNotNull(nonExistent);
    assertNotNull(nonExistent.get("currentPath"));
  }

  @Test
  void testPickDirectoryStructure() {
    System.setProperty("test.headless", "true");
    try {
      DiagramResource resource = new DiagramResource();
      Map<String, Object> response = resource.pickDirectory();
      assertNotNull(response);
      assertFalse((Boolean) response.get("success"));
      assertEquals("headless", response.get("reason"));
    } finally {
      System.clearProperty("test.headless");
    }
  }

  @Test
  void testContainerWorkspaceDetection(@TempDir Path tempDir) throws IOException {
    Path projectFile = tempDir.resolve("pom.xml");
    Files.writeString(projectFile, "<project></project>");

    System.setProperty("archlens.container.workspace", tempDir.toString());
    try {
      DiagramResource resource = new DiagramResource();

      // Test normalizeRoot defaults to container workspace
      String resolved = resource.normalizeRoot(".");
      assertEquals(tempDir.toRealPath().toString(), resolved);

      // Test listDirectories defaults to container workspace
      Map<String, Object> result = resource.listDirectories(null);
      assertNotNull(result);
      assertEquals(tempDir.toRealPath().toString(), result.get("currentPath"));

      // Verify quickNav contains Mounted Workspace
      @SuppressWarnings("unchecked")
      List<Map<String, String>> quickNav = (List<Map<String, String>>) result.get("quickNav");
      assertNotNull(quickNav);
      Map<String, String> workspaceNav =
          quickNav.stream()
              .filter(n -> "Mounted Workspace".equals(n.get("name")))
              .findFirst()
              .orElseThrow();
      assertEquals(tempDir.toRealPath().toString(), workspaceNav.get("path"));
    } finally {
      System.clearProperty("archlens.container.workspace");
    }
  }

  @Test
  void testPickDirectoryContainerReason(@TempDir Path tempDir) {
    String originalOs = System.getProperty("os.name");
    System.setProperty("archlens.container.workspace", tempDir.toString());
    System.setProperty("os.name", "Linux");
    try {
      DiagramResource resource = new DiagramResource();
      Map<String, Object> response = resource.pickDirectory();
      assertNotNull(response);
      assertFalse((Boolean) response.get("success"));
      assertFalse((Boolean) response.get("supported"));
      assertEquals("container", response.get("reason"));
    } finally {
      System.setProperty("os.name", originalOs);
      System.clearProperty("archlens.container.workspace");
    }
  }
}
