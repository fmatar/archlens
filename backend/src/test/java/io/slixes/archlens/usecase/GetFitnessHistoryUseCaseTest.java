package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slixes.archlens.domain.fitness.ArchitectureFitnessCalculator;
import io.slixes.archlens.domain.fitness.FitnessHistoryTrend;
import io.slixes.archlens.domain.fitness.HistoricalFitnessSnapshot;
import io.slixes.archlens.engine.GraphCompiler;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GetFitnessHistoryUseCaseTest {

  @Test
  void testGetHistoryEvaluatesHistoricalGitTagsInsteadOfCloningCurrentWorkspace(
      @TempDir Path tempDir) throws Exception {
    Path repo = ManageSnapshotsUseCaseTest.initTwoTagJavaRepo(tempDir);
    GraphCompiler compiler = ManageSnapshotsUseCaseTest.createRealJavaCompiler();
    ManageSnapshotsUseCase snapshotsUseCase =
        new ManageSnapshotsUseCase(compiler, new ObjectMapper());
    GetFitnessHistoryUseCase historyUseCase =
        new GetFitnessHistoryUseCase(
            snapshotsUseCase, compiler, new ArchitectureFitnessCalculator());

    FitnessHistoryTrend trend = historyUseCase.getHistory(repo.toString());
    assertNotNull(trend);

    HistoricalFitnessSnapshot snapV010 =
        trend.history().stream()
            .filter(s -> "v0.1.0".equals(s.snapshotId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected v0.1.0 in fitness history"));
    HistoricalFitnessSnapshot snapV020 =
        trend.history().stream()
            .filter(s -> "v0.2.0".equals(s.snapshotId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected v0.2.0 in fitness history"));

    assertEquals(1, snapV010.violationsCount(), "v0.1.0 must reflect its historical violation");
    assertEquals(0, snapV020.violationsCount(), "v0.2.0 must reflect clean architecture");
    assertTrue(
        snapV020.fitnessScore() > snapV010.fitnessScore(),
        "Clean v0.2.0 fitness score must exceed violating v0.1.0 score");
    assertEquals("IMPROVING", trend.trendDirection());
  }
}
