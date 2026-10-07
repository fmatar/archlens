package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavePolicyUseCaseTest {

  @Test
  @DisplayName("execute successfully writes policy to .archlens/policy.json and recompiles graph")
  void testSuccessfulSavePolicy(@TempDir Path tempDir) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    ArchitectureCompiler mockCompiler =
        new ArchitectureCompiler() {
          @Override
          public ArchitectureGraph compileGraph(String projectRoot, String proposalId) {
            return new ArchitectureGraph(
                "Updated Policy", false, null, List.of(), List.of(), List.of());
          }

          @Override
          public ArchitecturePolicy loadPolicy(String projectRoot) {
            return null;
          }
        };

    SavePolicyUseCase useCase = new SavePolicyUseCase(mockCompiler, mapper);

    ArchitecturePolicy policy =
        new ArchitecturePolicy(
            "Test Workbench",
            "src/main/java",
            "com.test",
            true,
            List.of("domain", "application", "adapter", "infrastructure"),
            List.of(
                List.of("domain"),
                List.of("application"),
                List.of("adapter"),
                List.of("infrastructure")),
            List.of(),
            List.of(),
            List.of(),
            "java");

    ArchitectureGraph result = useCase.execute(tempDir.toString(), policy);

    assertNotNull(result);
    assertEquals("Updated Policy", result.title());

    Path policyFile = tempDir.resolve(".archlens").resolve("policy.json");
    assertTrue(Files.exists(policyFile), "Expected .archlens/policy.json to be created");

    ArchitecturePolicy saved = mapper.readValue(policyFile.toFile(), ArchitecturePolicy.class);
    assertEquals("Test Workbench", saved.title());
    assertEquals(4, saved.levels().size());
  }

  @Test
  @DisplayName("execute rejects null policy or empty levels")
  void testRejectsInvalidPolicy(@TempDir Path tempDir) {
    SavePolicyUseCase useCase = new SavePolicyUseCase(null, new ObjectMapper());

    assertThrows(IllegalArgumentException.class, () -> useCase.execute(tempDir.toString(), null));

    ArchitecturePolicy emptyLevelsPolicy =
        new ArchitecturePolicy(
            "Invalid",
            "src",
            "com.test",
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "java");

    assertThrows(
        IllegalArgumentException.class,
        () -> useCase.execute(tempDir.toString(), emptyLevelsPolicy));
  }
}
