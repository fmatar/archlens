package com.design.umlviewer.scanner;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.metrics.CrapScoreCalculator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaAstScannerTest {

  @Test
  void testScanProject(@TempDir Path tempDir) throws IOException {
    JavaAstScanner scanner = new JavaAstScanner();
    // Inject dependencies manually for unit test
    java.lang.reflect.Field field;
    try {
      field = JavaAstScanner.class.getDeclaredField("crapCalculator");
      field.setAccessible(true);
      field.set(scanner, new CrapScoreCalculator());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // 1. Scan non-existing folder
    JavaAstScanner.ScanResult emptyResult =
        scanner.scanProject(tempDir.toString(), "non_existing", "com.test");
    assertTrue(emptyResult.classes().isEmpty());
    assertTrue(emptyResult.edges().isEmpty());

    // 2. Create sample Java files
    Path src = tempDir.resolve("src/main/java/com/test");
    Files.createDirectories(src);

    // Interface
    String serviceInterface =
        """
                package com.test;
                public interface OrderService {
                    void processOrder(String id);
                }
                """;
    Files.writeString(src.resolve("OrderService.java"), serviceInterface);

    // Class implementing interface with fields and methods
    String serviceImpl =
        """
                package com.test;
                import com.test.OrderService;
                public class OrderServiceImpl implements OrderService {
                    private String serviceName;
                    public void processOrder(String id) {
                        if (id != null) {
                            for (int i = 0; i < 1; i++) {}
                        }
                    }
                }
                """;
    Files.writeString(src.resolve("OrderServiceImpl.java"), serviceImpl);

    // Record
    String dataRecord =
        """
                package com.test;
                public record OrderData(String id, double amount) {}
                """;
    Files.writeString(src.resolve("OrderData.java"), dataRecord);

    // 3. Scan project
    JavaAstScanner.ScanResult result =
        scanner.scanProject(tempDir.toString(), "src/main/java", "com.test");

    assertEquals(3, result.classes().size());
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("OrderService")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("OrderServiceImpl")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("OrderData")));

    // Verify edges
    assertFalse(result.edges().isEmpty());
    assertTrue(result.edges().stream().anyMatch(e -> e.kind() == DependencyEdge.Kind.IMPLEMENTS));
  }

  @Test
  void testMultiModuleScanProject(@TempDir Path tempDir) throws IOException {
    JavaAstScanner scanner = new JavaAstScanner();
    try {
      java.lang.reflect.Field field = JavaAstScanner.class.getDeclaredField("crapCalculator");
      field.setAccessible(true);
      field.set(scanner, new CrapScoreCalculator());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // Create multi-module layout: module-api and module-core
    Path apiSrc = tempDir.resolve("module-api/src/main/java/com/example/api");
    Path coreSrc = tempDir.resolve("module-core/src/main/java/com/example/core");
    Files.createDirectories(apiSrc);
    Files.createDirectories(coreSrc);

    Files.writeString(
        apiSrc.resolve("UserApi.java"),
        "package com.example.api;\npublic interface UserApi { void getUser(); }");
    Files.writeString(
        coreSrc.resolve("UserService.java"),
        "package com.example.core;\nimport com.example.api.UserApi;\npublic class UserService implements UserApi { public void getUser() {} }");

    // Scan from root directory without specifying submodule
    JavaAstScanner.ScanResult result =
        scanner.scanProject(tempDir.toString(), "src/main/java", "com.example");

    assertEquals(2, result.classes().size());
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("UserApi")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("UserService")));
    assertFalse(result.edges().isEmpty());
  }
}
