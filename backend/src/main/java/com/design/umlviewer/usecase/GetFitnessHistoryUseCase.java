package com.design.umlviewer.usecase;

import com.design.umlviewer.domain.fitness.ArchitectureFitnessCalculator;
import com.design.umlviewer.domain.fitness.FitnessEvaluation;
import com.design.umlviewer.domain.fitness.FitnessHistoryTrend;
import com.design.umlviewer.domain.fitness.FitnessThresholds;
import com.design.umlviewer.domain.fitness.HistoricalFitnessSnapshot;
import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Level 1 Application Use Case orchestrating historical architectural fitness trend evaluation
 * across git tags and recorded snapshots.
 */
@ApplicationScoped
public class GetFitnessHistoryUseCase {

  private static final Logger LOG = Logger.getLogger(GetFitnessHistoryUseCase.class);

  @Inject ManageSnapshotsUseCase manageSnapshotsUseCase;
  @Inject ArchitectureCompiler architectureCompiler;
  @Inject ArchitectureFitnessCalculator fitnessCalculator;

  public GetFitnessHistoryUseCase() {}

  public GetFitnessHistoryUseCase(
      ManageSnapshotsUseCase manageSnapshotsUseCase,
      ArchitectureCompiler architectureCompiler,
      ArchitectureFitnessCalculator fitnessCalculator) {
    this.manageSnapshotsUseCase = manageSnapshotsUseCase;
    this.architectureCompiler = architectureCompiler;
    this.fitnessCalculator = fitnessCalculator;
  }

  public FitnessHistoryTrend getHistory(String projectRoot) {
    String root = WorkspacePathResolver.normalizeRoot(projectRoot);
    List<HistoricalFitnessSnapshot> history = new ArrayList<>();

    ArchitecturePolicy policy = architectureCompiler.loadPolicy(root);
    FitnessThresholds thresholds =
        (policy != null && policy.fitness() != null)
            ? policy.fitness()
            : FitnessThresholds.defaultThresholds();

    ArchitectureGraph currentGraph = null;
    try {
      currentGraph = architectureCompiler.compileGraph(root, null);
    } catch (IOException e) {
      LOG.warnf(
          "Could not compile current workspace graph for fitness history: %s", e.getMessage());
    }

    List<Map<String, Object>> snapshotList = manageSnapshotsUseCase.listSnapshots(root);

    // Snapshots from manageSnapshotsUseCase are newest first; reverse so oldest is first
    List<Map<String, Object>> chronologicalSnapshots = new ArrayList<>(snapshotList);
    Collections.reverse(chronologicalSnapshots);

    File snapshotsDir = new File(root, ".archlens/snapshots");
    File cacheDir = new File(root, ".archlens/cache");

    for (Map<String, Object> snap : chronologicalSnapshots) {
      String id = (String) snap.get("id");
      String label = (String) snap.getOrDefault("label", id);
      String date =
          (String)
              snap.getOrDefault(
                  "date", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()));

      try {
        ArchitectureGraph graph;
        File snapFile = new File(snapshotsDir, id + ".json");
        File cacheFile = new File(cacheDir, id + ".json");
        if (snapFile.exists() || cacheFile.exists()) {
          graph = manageSnapshotsUseCase.getSnapshot(id, root);
        } else {
          graph = currentGraph;
        }
        if (graph != null) {
          FitnessEvaluation eval = fitnessCalculator.evaluate(graph, thresholds);
          int violations =
              ((Number) eval.summaryMetrics().getOrDefault("violations", 0)).intValue();
          int cycles = ((Number) eval.summaryMetrics().getOrDefault("cycles", 0)).intValue();
          double screaming =
              ((Number) eval.summaryMetrics().getOrDefault("screamingScore", 0.0)).doubleValue();
          double maxDistance =
              ((Number) eval.summaryMetrics().getOrDefault("maxDistance", 0.0)).doubleValue();

          history.add(
              new HistoricalFitnessSnapshot(
                  id,
                  label,
                  date,
                  eval.fitnessScore(),
                  eval.grade(),
                  violations,
                  cycles,
                  screaming,
                  maxDistance));
        }
      } catch (Exception e) {
        LOG.debugf("Skipping unreadable snapshot %s: %s", id, e.getMessage());
      }
    }

    // Always append current working state as latest point
    if (currentGraph != null) {
      FitnessEvaluation currentEval = fitnessCalculator.evaluate(currentGraph, thresholds);
      int violations =
          ((Number) currentEval.summaryMetrics().getOrDefault("violations", 0)).intValue();
      int cycles = ((Number) currentEval.summaryMetrics().getOrDefault("cycles", 0)).intValue();
      double screaming =
          ((Number) currentEval.summaryMetrics().getOrDefault("screamingScore", 0.0)).doubleValue();
      double maxDistance =
          ((Number) currentEval.summaryMetrics().getOrDefault("maxDistance", 0.0)).doubleValue();

      String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
      history.add(
          new HistoricalFitnessSnapshot(
              "current",
              "Current (Workspace)",
              today,
              currentEval.fitnessScore(),
              currentEval.grade(),
              violations,
              cycles,
              screaming,
              maxDistance));
    }

    return fitnessCalculator.calculateTrend(history);
  }
}
