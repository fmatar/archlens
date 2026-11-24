package io.slixes.archlens.delivery.rest;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.mcp.ArchlensMcpService;
import io.slixes.archlens.usecase.FilesystemGateway;
import io.slixes.archlens.usecase.ResolveFilesystemUseCase;
import io.slixes.archlens.usecase.TestRunnerGateway;
import io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

/**
 * Governance tests for #246: no deprecated forwarding facades or duplicate CRAP interfaces,
 * constructor injection only on REST/MCP delivery beans, and I/O isolated behind ports.
 */
class ResourceDiGovernanceTest {

  @Test
  void deprecatedFacadesAndDuplicateCalculatorsAreRemoved() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("io.slixes.archlens.delivery.rest.WorkspacePathResolver"),
        "Deprecated delivery.rest.WorkspacePathResolver facade must be removed");
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("io.slixes.archlens.metrics.CrapCalculator"),
        "Duplicate metrics.CrapCalculator interface must be consolidated into CrapScoreCalculator");
  }

  @Test
  void deliveryAdaptersUseConstructorInjectionOnly() {
    List<Class<?>> adapters =
        List.of(
            DiagramResource.class,
            FitnessResource.class,
            MailboxResource.class,
            ProjectFilesystemResource.class,
            SnapshotResource.class,
            VersionResource.class,
            JaxRsSessionTokenFilter.class,
            SessionTokenFilter.class,
            SessionTokenService.class,
            ArchlensMcpService.class);

    for (Class<?> adapter : adapters) {
      List<String> injectedFields =
          Arrays.stream(adapter.getDeclaredFields())
              .filter(f ->
                  f.isAnnotationPresent(Inject.class)
                      || f.isAnnotationPresent(ConfigProperty.class))
              .map(Field::getName)
              .toList();
      assertEquals(
          List.of(),
          injectedFields,
          adapter.getSimpleName() + " must not use @Inject or @ConfigProperty field injection");

      long injectConstructors =
          Arrays.stream(adapter.getDeclaredConstructors())
              .filter(c -> c.isAnnotationPresent(Inject.class))
              .count();
      assertEquals(
          1L,
          injectConstructors,
          adapter.getSimpleName() + " must declare exactly one @Inject constructor");
    }
  }

  @Test
  void useCasesDependOnPortsAndContainNoDirectFilesystemOrProcessBytecode() throws IOException {
    Constructor<?> fsCtor =
        Arrays.stream(ResolveFilesystemUseCase.class.getDeclaredConstructors())
            .filter(c -> c.isAnnotationPresent(Inject.class))
            .findFirst()
            .orElseThrow();
    assertArrayEquals(new Class<?>[] {FilesystemGateway.class}, fsCtor.getParameterTypes());

    Constructor<?> verifyCtor =
        Arrays.stream(VerifyAndRollbackRefactorUseCase.class.getDeclaredConstructors())
            .filter(c -> c.isAnnotationPresent(Inject.class))
            .findFirst()
            .orElseThrow();
    assertTrue(Arrays.asList(verifyCtor.getParameterTypes()).contains(FilesystemGateway.class));
    assertTrue(Arrays.asList(verifyCtor.getParameterTypes()).contains(TestRunnerGateway.class));

    for (Class<?> useCaseClass :
        List.of(ResolveFilesystemUseCase.class, VerifyAndRollbackRefactorUseCase.class)) {
      String classResource = useCaseClass.getSimpleName() + ".class";
      try (InputStream in = useCaseClass.getResourceAsStream(classResource)) {
        assertNotNull(in, "Compiled bytecode must be loadable for " + useCaseClass.getName());
        String constantPoolAscii = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        assertFalse(
            constantPoolAscii.contains("java/lang/ProcessBuilder"),
            useCaseClass.getSimpleName() + " bytecode must not invoke ProcessBuilder");
        assertFalse(
            constantPoolAscii.contains("java/nio/file/Files"),
            useCaseClass.getSimpleName() + " bytecode must not invoke java.nio.file.Files");
      }
    }
  }
}
