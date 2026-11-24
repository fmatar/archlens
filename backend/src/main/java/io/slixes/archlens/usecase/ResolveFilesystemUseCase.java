package io.slixes.archlens.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Level 1 Application Use Case coordinating project discovery, filesystem directory browsing,
 * native directory picker dialogs, and confined source code reading via {@link FilesystemGateway}.
 */
@ApplicationScoped
public class ResolveFilesystemUseCase {

  private FilesystemGateway filesystemGateway;
  private List<Path> configuredAllowedRoots;

  ResolveFilesystemUseCase() {}

  @Inject
  public ResolveFilesystemUseCase(FilesystemGateway filesystemGateway) {
    this(filesystemGateway, null);
  }

  public ResolveFilesystemUseCase(
      FilesystemGateway filesystemGateway, List<Path> configuredAllowedRoots) {
    this.filesystemGateway = filesystemGateway;
    this.configuredAllowedRoots = configuredAllowedRoots;
  }

  public Map<String, Object> listProjects() {
    return filesystemGateway.listProjects();
  }

  public Map<String, Object> listDirectories(String rawPath) {
    return filesystemGateway.listDirectories(rawPath, configuredAllowedRoots);
  }

  public Map<String, Object> pickDirectory() {
    return filesystemGateway.pickDirectory();
  }

  public Map<String, Object> getSourceCode(String filePath, int line, String projectRoot) {
    if (filePath == null || filePath.isBlank()) {
      return Map.of("error", "No filePath provided", "content", "");
    }
    return filesystemGateway.readConfinedSourceCode(filePath, line, projectRoot);
  }
}
