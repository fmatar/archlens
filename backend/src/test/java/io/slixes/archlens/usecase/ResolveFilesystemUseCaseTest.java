package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResolveFilesystemUseCaseTest {

  @Test
  void testListDirectoriesAndProjects(@TempDir Path tempDir) throws IOException {
    ResolveFilesystemUseCase useCase = new ResolveFilesystemUseCase();

    // Create a Maven project
    Path mavenApp = tempDir.resolve("my-maven-app");
    Files.createDirectories(mavenApp.resolve("src/main/java"));
    Files.writeString(mavenApp.resolve("pom.xml"), "<project></project>");

    // Create a regular folder
    Path regularFolder = tempDir.resolve("docs-folder");
    Files.createDirectories(regularFolder);

    Map<String, Object> dirResult = useCase.listDirectories(tempDir.toString());
    assertNotNull(dirResult);
    assertTrue(dirResult.containsKey("currentPath"));
    assertTrue(dirResult.containsKey("directories"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> dirs = (List<Map<String, Object>>) dirResult.get("directories");
    assertEquals(2, dirs.size());

    Map<String, Object> mavenEntry =
        dirs.stream().filter(d -> "my-maven-app".equals(d.get("name"))).findFirst().orElseThrow();
    assertEquals(Boolean.TRUE, mavenEntry.get("isProject"));
    assertEquals("Maven", mavenEntry.get("projectType"));

    Map<String, Object> docsEntry =
        dirs.stream().filter(d -> "docs-folder".equals(d.get("name"))).findFirst().orElseThrow();
    assertEquals(Boolean.FALSE, docsEntry.get("isProject"));
  }

  @Test
  void testGetSourceCode(@TempDir Path tempDir) throws IOException {
    ResolveFilesystemUseCase useCase = new ResolveFilesystemUseCase();

    Path testFile = tempDir.resolve("TestFile.java");
    Files.writeString(testFile, "public class TestFile {\n  public void hello() {}\n}\n");

    Map<String, Object> result = useCase.getSourceCode(testFile.toString(), 2, tempDir.toString());
    assertNotNull(result);
    assertEquals("TestFile.java", result.get("fileName"));
    assertEquals(2, result.get("targetLine"));
    assertTrue(((String) result.get("content")).contains("hello()"));

    // Missing file fallback
    Map<String, Object> missing = useCase.getSourceCode("missing-file.txt", 1, tempDir.toString());
    assertNotNull(missing);
    assertTrue(missing.containsKey("error"));
  }

  @Test
  void testListProjectsDiscovery() {
    ResolveFilesystemUseCase useCase = new ResolveFilesystemUseCase();
    Map<String, Object> projects = useCase.listProjects();
    assertNotNull(projects);
    assertTrue(projects.containsKey("current"));
    assertTrue(projects.containsKey("discovered"));
  }

  @Test
  void testPickDirectoryHeadless() {
    System.setProperty("test.headless", "true");
    try {
      ResolveFilesystemUseCase useCase = new ResolveFilesystemUseCase();
      Map<String, Object> response = useCase.pickDirectory();
      assertNotNull(response);
      assertFalse((Boolean) response.get("success"));
      assertEquals("headless", response.get("reason"));
    } finally {
      System.clearProperty("test.headless");
    }
  }
}
