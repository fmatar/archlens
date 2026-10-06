package io.slixes.archlens.delivery.rest;

import io.slixes.archlens.domain.fitness.FitnessEvaluation;
import io.slixes.archlens.domain.fitness.FitnessHistoryTrend;
import io.slixes.archlens.usecase.EvaluateFitnessUseCase;
import io.slixes.archlens.usecase.GetFitnessHistoryUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;

/**
 * Level 3 Delivery Resource serving architectural fitness function evaluations and historical
 * regression trends.
 */
@Path("/api/fitness")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FitnessResource {

  private static final String DEFAULT_PROJECT_ROOT = ".";

  @Inject EvaluateFitnessUseCase evaluateFitnessUseCase;
  @Inject GetFitnessHistoryUseCase getFitnessHistoryUseCase;

  public FitnessResource() {}

  public FitnessResource(
      EvaluateFitnessUseCase evaluateFitnessUseCase,
      GetFitnessHistoryUseCase getFitnessHistoryUseCase) {
    this.evaluateFitnessUseCase = evaluateFitnessUseCase;
    this.getFitnessHistoryUseCase = getFitnessHistoryUseCase;
  }

  @GET
  public FitnessEvaluation getFitness(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return evaluateFitnessUseCase.evaluateCurrent(projectRoot, proposalId);
  }

  @GET
  @Path("/history")
  public FitnessHistoryTrend getFitnessHistory(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return getFitnessHistoryUseCase.getHistory(projectRoot);
  }
}
