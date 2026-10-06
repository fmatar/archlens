package com.design.umlviewer.delivery.rest;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backward-compatible forwarding facade for {@link
 * com.design.umlviewer.usecase.WorkspacePathResolver}.
 *
 * @deprecated Use {@link com.design.umlviewer.usecase.WorkspacePathResolver} directly.
 */
@Deprecated
public final class WorkspacePathResolver {

  public static final String DEFAULT_PROJECT_ROOT =
      com.design.umlviewer.usecase.WorkspacePathResolver.DEFAULT_PROJECT_ROOT;

  private WorkspacePathResolver() {
    // Forwarding utility class
  }

  public static boolean isContainerEnvironment() {
    return com.design.umlviewer.usecase.WorkspacePathResolver.isContainerEnvironment();
  }

  public static String normalizeRoot(String root) {
    return com.design.umlviewer.usecase.WorkspacePathResolver.normalizeRoot(root);
  }

  public static boolean isMacOs() {
    return com.design.umlviewer.usecase.WorkspacePathResolver.isMacOs();
  }

  public static boolean isIgnoredDirectory(File dir) {
    return com.design.umlviewer.usecase.WorkspacePathResolver.isIgnoredDirectory(dir);
  }

  public static boolean isProjectDirectory(File dir) {
    return com.design.umlviewer.usecase.WorkspacePathResolver.isProjectDirectory(dir);
  }

  public static void scanDirectoryForProjects(
      File dir,
      String prefix,
      List<Map<String, String>> discovered,
      Set<String> seenPaths,
      Set<String> visitedDirs,
      int maxDepth) {
    com.design.umlviewer.usecase.WorkspacePathResolver.scanDirectoryForProjects(
        dir, prefix, discovered, seenPaths, visitedDirs, maxDepth);
  }
}
