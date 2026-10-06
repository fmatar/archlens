package io.slixes.archlens.delivery.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.engine.ArchitectureCompiler;
import io.slixes.archlens.usecase.ManageSnapshotsUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Map;

/**
 * REST endpoint managing historical snapshots and baseline architecture models. Delegates
 * application orchestration to {@link ManageSnapshotsUseCase}.
 */
@Path("/api/snapshots")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SnapshotResource {

  private static final String DEFAULT_PROJECT_ROOT = ".";

  @Inject ManageSnapshotsUseCase manageSnapshotsUseCase;

  public SnapshotResource() {}

  public SnapshotResource(ManageSnapshotsUseCase manageSnapshotsUseCase) {
    this.manageSnapshotsUseCase = manageSnapshotsUseCase;
  }

  public SnapshotResource(ArchitectureCompiler graphCompiler, ObjectMapper mapper) {
    this.manageSnapshotsUseCase = new ManageSnapshotsUseCase(graphCompiler, mapper);
  }

  @GET
  public Map<String, Object> listSnapshots(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return Map.of("snapshots", manageSnapshotsUseCase.listSnapshots(projectRoot));
  }

  @GET
  @Path("/{snapshotId}")
  public ArchitectureGraph getSnapshot(
      @PathParam("snapshotId") String snapshotId,
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot)
      throws IOException {
    try {
      return manageSnapshotsUseCase.getSnapshot(snapshotId, projectRoot);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
