package io.slixes.archlens.delivery.rest;

import io.slixes.archlens.usecase.ResolveFilesystemUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/**
 * REST endpoint handling project discovery, filesystem directory browsing, native directory picker
 * dialogs, and source code streaming. Delegates application orchestration to {@link
 * ResolveFilesystemUseCase}.
 */
@Path("/api")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectFilesystemResource {

  @Inject ResolveFilesystemUseCase filesystemUseCase;

  public ProjectFilesystemResource() {
    this.filesystemUseCase = new ResolveFilesystemUseCase();
  }

  public ProjectFilesystemResource(ResolveFilesystemUseCase filesystemUseCase) {
    this.filesystemUseCase =
        filesystemUseCase != null ? filesystemUseCase : new ResolveFilesystemUseCase();
  }

  @GET
  @Path("/projects")
  public Map<String, Object> listProjects() {
    return filesystemUseCase.listProjects();
  }

  @GET
  @Path("/fs/directories")
  public Map<String, Object> listDirectories(@QueryParam("path") String rawPath) {
    return filesystemUseCase.listDirectories(rawPath);
  }

  @POST
  @Path("/fs/pick-directory")
  public Map<String, Object> pickDirectory() {
    return filesystemUseCase.pickDirectory();
  }

  public Map<String, Object> getSourceCode(String filePath, int line) {
    return filesystemUseCase.getSourceCode(filePath, line, null);
  }

  @GET
  @Path("/source")
  public Map<String, Object> getSourceCode(
      @QueryParam("filePath") String filePath,
      @QueryParam("line") @DefaultValue("1") int line,
      @QueryParam("projectRoot") String projectRoot) {
    return filesystemUseCase.getSourceCode(filePath, line, projectRoot);
  }
}
