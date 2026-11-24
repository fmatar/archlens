package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResolveFilesystemUseCaseTest {

  private static final FilesystemGateway IN_MEMORY_GATEWAY =
      new FilesystemGateway() {
        @Override
        public Map<String, Object> listProjects() {
          return Map.of("current", Map.of("name", "stub", "path", "."), "discovered", List.of());
        }

        @Override
        public Map<String, Object> listDirectories(String rawPath, List<Path> allowedRoots) {
          if (allowedRoots != null
              && rawPath != null
              && allowedRoots.stream().noneMatch(r -> Path.of(rawPath).startsWith(r))) {
            return Map.of("denied", true, "path", rawPath);
          }
          return Map.of("currentPath", rawPath != null ? rawPath : ".", "directories", List.of());
        }

        @Override
        public Map<String, Object> pickDirectory() {
          return Map.of("success", false, "supported", false, "reason", "headless");
        }

        @Override
        public Map<String, Object> readConfinedSourceCode(
            String filePath, int line, String projectRoot) {
          if (filePath.startsWith("../")) {
            return Map.of("denied", true, "filePath", filePath, "content", "");
          }
          return Map.of(
              "fileName",
              "Order.java",
              "filePath",
              filePath,
              "content",
              "public class Order {}",
              "targetLine",
              line);
        }

        @Override
        public String createPreRefactorSnapshot(String projectRoot) {
          return "snap-stub";
        }

        @Override
        public VerifyAndRollbackRefactorUseCase.RollbackResult restoreSnapshot(
            String projectRoot, String snapshotId) {
          return new VerifyAndRollbackRefactorUseCase.RollbackResult(false, "", List.of());
        }
      };

  @Test
  void delegatesDiscoveryBrowsingPickerAndSourceReadsThroughInMemoryPort() {
    Path allowed = Path.of("/virtual/allowed");
    ResolveFilesystemUseCase useCase =
        new ResolveFilesystemUseCase(IN_MEMORY_GATEWAY, List.of(allowed));

    assertTrue(useCase.listProjects().containsKey("current"));
    assertEquals("headless", useCase.pickDirectory().get("reason"));
    assertEquals(Boolean.TRUE, useCase.listDirectories("/virtual/outside").get("denied"));
    assertNull(useCase.listDirectories("/virtual/allowed/sub").get("denied"));

    ResolveFilesystemUseCase defaultUseCase = new ResolveFilesystemUseCase(IN_MEMORY_GATEWAY);
    assertEquals("", defaultUseCase.getSourceCode(null, 1, ".").get("content"));
    assertEquals("", defaultUseCase.getSourceCode("   ", 1, ".").get("content"));

    Map<String, Object> ok = defaultUseCase.getSourceCode("src/Order.java", 7, "/virtual/allowed");
    assertEquals("Order.java", ok.get("fileName"));
    assertEquals(7, ok.get("targetLine"));
    assertEquals("public class Order {}", ok.get("content"));

    Map<String, Object> denied =
        defaultUseCase.getSourceCode("../secret.env", 1, "/virtual/allowed");
    assertEquals(Boolean.TRUE, denied.get("denied"));
  }
}
