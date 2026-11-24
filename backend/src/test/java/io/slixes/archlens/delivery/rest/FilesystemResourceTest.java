package io.slixes.archlens.delivery.rest;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.engine.ProjectFileWatcher;
import io.slixes.archlens.usecase.OpenedProjectRegistry;
import io.slixes.archlens.usecase.ResolveFilesystemUseCase;
import io.slixes.archlens.usecase.WorkspacePathResolver;
import io.slixes.archlens.workspace.fs.LocalFilesystemGateway;
import jakarta.ws.rs.WebApplicationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemResourceTest {

  private static ProjectFilesystemResource newResource() {
    return new ProjectFilesystemResource(
        new ResolveFilesystemUseCase(new LocalFilesystemGateway()),
        new OpenedProjectRegistry(),
        new ProjectFileWatcher());
  }

  @Test
  void testListDirectoriesWithProjects(@TempDir Path tempDir) throws IOException {
    ProjectFilesystemResource resource = newResource();

    Path mavenProject = tempDir.resolve("my-maven-app");
    Files.createDirectories(mavenProject.resolve("src/main/java"));
    Files.writeString(mavenProject.resolve("pom.xml"), "<project></project>");

    Path gradleProject = tempDir.resolve("my-gradle-app");
    Files.createDirectories(gradleProject);
    Files.writeString(gradleProject.resolve("build.gradle"), "// gradle");

    Path javaProject = tempDir.resolve("my-java-app");
    Files.createDirectories(javaProject.resolve("src/main/java"));

    Path nodeProject = tempDir.resolve("my-node-app");
    Files.createDirectories(nodeProject);
    Files.writeString(nodeProject.resolve("package.json"), "{}");

    Path gitProject = tempDir.resolve("my-git-app");
    Files.createDirectories(gitProject.resolve(".git"));

    Path regularFolder = tempDir.resolve("regular-folder");
    Files.createDirectories(regularFolder);

    Files.createDirectories(tempDir.resolve(".hidden"));
    Files.createDirectories(tempDir.resolve("node_modules"));

    System.setProperty("archlens.allowed.roots", tempDir.toString());
    Map<String, Object> result;
    try {
      result = resource.listDirectories(tempDir.toString());
    } finally {
      System.clearProperty("archlens.allowed.roots");
    }
    assertNotNull(result);
    assertEquals(tempDir.toRealPath().toString(), result.get("currentPath"));
    assertNotNull(result.get("breadcrumbs"));
    assertNotNull(result.get("quickNav"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> directories = (List<Map<String, Object>>) result.get("directories");
    assertNotNull(directories);
    assertEquals(6, directories.size());

    Map<String, Object> mavenEntry =
        directories.stream()
            .filter(d -> "my-maven-app".equals(d.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals(Boolean.TRUE, mavenEntry.get("isProject"));
    assertEquals("Maven", mavenEntry.get("projectType"));

    Map<String, Object> gradleEntry =
        directories.stream()
            .filter(d -> "my-gradle-app".equals(d.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals(Boolean.TRUE, gradleEntry.get("isProject"));
    assertEquals("Gradle", gradleEntry.get("projectType"));

    Map<String, Object> javaEntry =
        directories.stream()
            .filter(d -> "my-java-app".equals(d.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals("Java", javaEntry.get("projectType"));

    Map<String, Object> nodeEntry =
        directories.stream()
            .filter(d -> "my-node-app".equals(d.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals("Node", nodeEntry.get("projectType"));

    Map<String, Object> gitEntry =
        directories.stream()
            .filter(d -> "my-git-app".equals(d.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals("Git", gitEntry.get("projectType"));

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
    ProjectFilesystemResource resource = newResource();

    Map<String, Object> defaultResult = resource.listDirectories(null);
    assertNotNull(defaultResult);
    assertNotNull(defaultResult.get("currentPath"));
    assertNotNull(defaultResult.get("breadcrumbs"));

    Map<String, Object> blankResult = resource.listDirectories("   ");
    assertNotNull(blankResult);
    assertNotNull(blankResult.get("currentPath"));

    Map<String, Object> tildeResult = resource.listDirectories("~");
    assertNotNull(tildeResult.get("currentPath"));

    String missingInHome =
        Path.of(System.getProperty("user.home"), "archlens-non-existent-test-dir-12345").toString();
    Map<String, Object> nonExistent = resource.listDirectories(missingInHome);
    assertNotNull(nonExistent);
    assertNotNull(nonExistent.get("currentPath"));

    WebApplicationException denied =
        assertThrows(
            WebApplicationException.class,
            () -> resource.listDirectories("/path/does/not/exist/at/all/hopefully/12345"));
    assertEquals(403, denied.getResponse().getStatus());
  }

  @Test
  void testOpenProjectAndSourceEndpoints(@TempDir Path tempDir) throws IOException {
    ProjectFilesystemResource resource = newResource();
    Path srcFile = tempDir.resolve("Demo.java");
    Files.writeString(srcFile, "class Demo {}");

    Map<String, Object> openedViaBody =
        resource.openProject(null, Map.of("projectRoot", tempDir.toString()));
    assertEquals(Boolean.TRUE, openedViaBody.get("opened"));
    assertTrue(resource.getOpenedProjects().isOpened(tempDir.toString()));

    WebApplicationException badOpen =
        assertThrows(
            WebApplicationException.class,
            () -> resource.openProject(tempDir.resolve("missing").toString(), null));
    assertEquals(400, badOpen.getResponse().getStatus());

    Map<String, Object> src = resource.getSourceCode("Demo.java", 1, tempDir.toString());
    assertEquals("class Demo {}", src.get("content"));

    Map<String, Object> missingInsideRoot =
        resource.getSourceCode("MissingInsideRoot.java", 10, tempDir.toString());
    assertTrue(missingInsideRoot.containsKey("error"));

    WebApplicationException outsideDefault =
        assertThrows(
            WebApplicationException.class,
            () -> resource.getSourceCode("/invalid/path/Test.java", 10));
    assertEquals(403, outsideDefault.getResponse().getStatus());
  }

  @Test
  void testPickDirectoryStructure() {
    System.setProperty("test.headless", "true");
    try {
      ProjectFilesystemResource resource = newResource();
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
    Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

    System.setProperty("archlens.container.workspace", tempDir.toString());
    try {
      ProjectFilesystemResource resource = newResource();
      String resolved = WorkspacePathResolver.normalizeRoot(".");
      assertEquals(tempDir.toRealPath().toString(), resolved);

      Map<String, Object> result = resource.listDirectories(null);
      assertNotNull(result);
      assertEquals(tempDir.toRealPath().toString(), result.get("currentPath"));

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
  void testPickDirectoryContainerAndUnsupportedOsReasons(@TempDir Path tempDir) {
    String originalOs = System.getProperty("os.name");
    System.setProperty("archlens.container.workspace", tempDir.toString());
    System.setProperty("os.name", "Linux");
    try {
      ProjectFilesystemResource resource = newResource();
      Map<String, Object> response = resource.pickDirectory();
      assertFalse((Boolean) response.get("success"));
      assertFalse((Boolean) response.get("supported"));
      assertEquals("container", response.get("reason"));

      System.clearProperty("archlens.container.workspace");
      Map<String, Object> linuxHost = resource.pickDirectory();
      assertFalse((Boolean) linuxHost.get("supported"));
      assertEquals("unsupported_os", linuxHost.get("reason"));
    } finally {
      System.setProperty("os.name", originalOs);
      System.clearProperty("archlens.container.workspace");
    }
  }
}
