package io.slixes.archlens.delivery.rest;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backward-compatible forwarding facade for {@link
 * io.slixes.archlens.usecase.WorkspacePathResolver}.
 *
 * @deprecated Use {@link io.slixes.archlens.usecase.WorkspacePathResolver} directly.
 */
@Deprecated
public final class WorkspacePathResolver {

  public static final String DEFAULT_PROJECT_ROOT =
      io.slixes.archlens.usecase.WorkspacePathResolver.DEFAULT_PROJECT_ROOT;

  private WorkspacePathResolver() {
    // Forwarding utility class
  }

  public static boolean isContainerEnvironment() {
    return io.slixes.archlens.usecase.WorkspacePathResolver.isContainerEnvironment();
  }

  public static String normalizeRoot(String root) {
    return io.slixes.archlens.usecase.WorkspacePathResolver.normalizeRoot(root);
  }

  public static boolean isMacOs() {
    return io.slixes.archlens.usecase.WorkspacePathResolver.isMacOs();
  }

  public static boolean isIgnoredDirectory(File dir) {
    return io.slixes.archlens.usecase.WorkspacePathResolver.isIgnoredDirectory(dir);
  }

  public static boolean isProjectDirectory(File dir) {
    return io.slixes.archlens.usecase.WorkspacePathResolver.isProjectDirectory(dir);
  }

  public static void scanDirectoryForProjects(
      File dir,
      String prefix,
      List<Map<String, String>> discovered,
      Set<String> seenPaths,
      Set<String> visitedDirs,
      int maxDepth) {
    io.slixes.archlens.usecase.WorkspacePathResolver.scanDirectoryForProjects(
        dir, prefix, discovered, seenPaths, visitedDirs, maxDepth);
  }
}
