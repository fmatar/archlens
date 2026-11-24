package io.slixes.archlens.usecase;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Output port (Ring 1) abstracting workspace project discovery, directory browsing, native folder
 * picker dialogs, confined source file reads, and transactional refactor workspace snapshots.
 */
public interface FilesystemGateway {

  Map<String, Object> listProjects();

  Map<String, Object> listDirectories(String rawPath, List<Path> configuredAllowedRoots);

  Map<String, Object> pickDirectory();

  Map<String, Object> readConfinedSourceCode(String filePath, int line, String projectRoot);

  String createPreRefactorSnapshot(String projectRoot) throws IOException;

  VerifyAndRollbackRefactorUseCase.RollbackResult restoreSnapshot(
      String projectRoot, String snapshotId) throws IOException;
}
