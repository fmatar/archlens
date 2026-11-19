package io.slixes.archlens.delivery.rest;

import io.slixes.archlens.engine.ProjectFileWatcher;
import io.slixes.archlens.usecase.OpenedProjectRegistry;
import io.slixes.archlens.usecase.ResolveFilesystemUseCase;
import io.slixes.archlens.usecase.WorkspacePathResolver;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * REST endpoint handling project discovery, explicit session project opening, filesystem directory
 * browsing, native directory picker dialogs, and source code streaming. Delegates application
 * orchestration to {@link ResolveFilesystemUseCase}.
 */
@Path("/api")
@ApplicationScoped
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectFilesystemResource {

  @Inject ResolveFilesystemUseCase filesystemUseCase;
  @Inject OpenedProjectRegistry openedProjects;
  @Inject ProjectFileWatcher fileWatcher;

  public ProjectFilesystemResource() {
    this.filesystemUseCase = new ResolveFilesystemUseCase();
  }

  public ProjectFilesystemResource(ResolveFilesystemUseCase filesystemUseCase) {
    this(filesystemUseCase, new OpenedProjectRegistry(), null);
  }

  public ProjectFilesystemResource(
      ResolveFilesystemUseCase filesystemUseCase, OpenedProjectRegistry openedProjects) {
    this(filesystemUseCase, openedProjects, null);
  }

  public ProjectFilesystemResource(
      ResolveFilesystemUseCase filesystemUseCase,
      OpenedProjectRegistry openedProjects,
      ProjectFileWatcher fileWatcher) {
    this.filesystemUseCase =
        filesystemUseCase != null ? filesystemUseCase : new ResolveFilesystemUseCase();
    this.openedProjects = openedProjects != null ? openedProjects : new OpenedProjectRegistry();
    this.fileWatcher = fileWatcher;
  }

  OpenedProjectRegistry getOpenedProjects() {
    if (openedProjects == null) {
      openedProjects = new OpenedProjectRegistry();
    }
    return openedProjects;
  }

  @GET
  @Path("/projects")
  public Map<String, Object> listProjects() {
    return filesystemUseCase.listProjects();
  }

  @POST
  @Path("/projects/open")
  public Map<String, Object> openProject(
      @QueryParam("projectRoot") String queryProjectRoot, Map<String, Object> body) {
    String rawRoot = queryProjectRoot;
    if ((rawRoot == null || rawRoot.isBlank())
        && body != null
        && body.get("projectRoot") instanceof String s) {
      rawRoot = s;
    }
    String normalized =
        WorkspacePathResolver.normalizeRoot(rawRoot == null || rawRoot.isBlank() ? "." : rawRoot);
    return getOpenedProjects()
        .markOpened(normalized)
        .<Map<String, Object>>map(canonical -> {
          if (fileWatcher != null) {
            fileWatcher.switchRootIfRunning(canonical);
          }
          return Map.of("opened", true, "projectRoot", canonical.toString());
        })
        .orElseThrow(() ->
            new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(
                        Map.of(
                            "opened",
                            false,
                            "error",
                            "Project root does not exist or is not a directory"))
                    .build()));
  }

  @GET
  @Path("/fs/directories")
  public Map<String, Object> listDirectories(@QueryParam("path") String rawPath) {
    return throwIfDenied(filesystemUseCase.listDirectories(rawPath));
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
    return throwIfDenied(filesystemUseCase.getSourceCode(filePath, line, projectRoot));
  }

  private static Map<String, Object> throwIfDenied(Map<String, Object> result) {
    if (Boolean.TRUE.equals(result.get("denied"))) {
      throw new WebApplicationException(
          Response.status(Response.Status.FORBIDDEN)
              .type(MediaType.APPLICATION_JSON)
              .entity(result)
              .build());
    }
    return result;
  }
}
