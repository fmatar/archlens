package io.slixes.archlens.usecase;

import java.nio.file.Path;
import java.util.List;

/**
 * Output port (Ring 1) abstracting polyglot build-tool test command discovery and bounded
 * process-tree execution during refactoring verification.
 */
@FunctionalInterface
public interface TestRunnerGateway {

  VerifyAndRollbackRefactorUseCase.TestExecutionOutcome runTests(String projectRoot);

  default List<String> detectTestCommand(Path rootPath) {
    return List.of();
  }
}
