package com.design.umlviewer.usecase;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Level 1 Application utility resolving filesystem paths, container environments, and project
 * hierarchies for the Archlens workbench.
 */
public final class WorkspacePathResolver {

  public static final String DEFAULT_PROJECT_ROOT = ".";

  private WorkspacePathResolver() {
    // Utility class
  }

  public static boolean isContainerEnvironment() {
    boolean isExplicitTest = System.getProperty("archlens.container.workspace") != null;
    boolean isContainerRuntime =
        new File("/work/quarkus-run.jar").exists()
            || new File("/work/application").exists()
            || new File("/.dockerenv").exists();
    return isExplicitTest || isContainerRuntime || !new File(".", "pom.xml").exists();
  }

  public static String normalizeRoot(String root) {
    if (root == null || root.isBlank()) {
      return DEFAULT_PROJECT_ROOT;
    }
    String normalized = root.trim();
    String userHome = System.getProperty("user.home", "");
    String labsPath = userHome + "/workspace/labs";
    String containerPath = System.getProperty("archlens.container.workspace", "/workspace");
    File containerWorkspace = new File(containerPath);
    if (normalized.equals(".")
        && isContainerEnvironment()
        && containerWorkspace.exists()
        && containerWorkspace.isDirectory()) {
      File[] contents = containerWorkspace.listFiles();
      if (contents != null && contents.length > 0) {
        try {
          return containerWorkspace.getCanonicalPath();
        } catch (IOException e) {
          return containerWorkspace.getAbsolutePath();
        }
      }
    }
    if (normalized.equals(".")) {
      if (!new File(".", ".archlens/policy.json").exists()
          && !new File(".", ".uml-viewer/policy.json").exists()
          && (new File("..", ".archlens/policy.json").exists()
              || new File("..", ".uml-viewer/policy.json").exists())) {
        try {
          return new File("..").getCanonicalPath();
        } catch (IOException e) {
          return new File("..").getAbsolutePath();
        }
      }
      if (!new File(".", "pom.xml").exists() && new File(labsPath, "archlens/pom.xml").exists()) {
        return labsPath + "/archlens";
      }
    }
    // Expand home directory shorthand ~
    if (normalized.startsWith("~")) {
      normalized = userHome + normalized.substring(1);
    }
    // Map /workspace/labs/ to host userHome/workspace/labs/ if present
    if (normalized.startsWith("/workspace/labs/")) {
      File hostLabs = new File(labsPath);
      if (hostLabs.exists() && hostLabs.isDirectory()) {
        normalized = normalized.replace("/workspace/labs", labsPath);
      }
    }
    // Transparently map agentlens or unclebob-design to archlens
    if (normalized.contains("agentlens")) {
      normalized = normalized.replace("agentlens", "archlens");
    }
    if (normalized.contains("unclebob-design")) {
      normalized = normalized.replace("unclebob-design", "archlens");
    }
    return normalized;
  }

  public static boolean isMacOs() {
    String os = System.getProperty("os.name", "");
    return os.contains("Mac")
        || os.contains("mac")
        || os.contains("Darwin")
        || os.contains("darwin");
  }

  public static boolean isProjectDirectory(File dir) {
    if (dir == null || !dir.isDirectory()) {
      return false;
    }
    return new File(dir, "pom.xml").exists()
        || new File(dir, "package.json").exists()
        || new File(dir, "go.mod").exists()
        || new File(dir, "Cargo.toml").exists()
        || new File(dir, "deps.edn").exists()
        || new File(dir, "build.gradle").exists()
        || new File(dir, "build.gradle.kts").exists()
        || new File(dir, "pyproject.toml").exists()
        || new File(dir, ".git").exists()
        || new File(dir, "src").exists();
  }

  public static boolean isIgnoredDirectory(File dir) {
    if (dir == null) {
      return true;
    }
    String name = dir.getName();
    if (name.startsWith(".")) {
      return true;
    }
    return name.equals("target")
        || name.equals("node_modules")
        || name.equals("build")
        || name.equals("dist")
        || name.equals("out")
        || name.equals("vendor")
        || name.equals("bin")
        || name.equals("obj");
  }

  public static boolean isSubProjectDirectory(File dir) {
    if (dir == null || !dir.isDirectory()) {
      return false;
    }
    String name = dir.getName();
    if (isIgnoredDirectory(dir)
        || name.equals("src")
        || name.equals("test")
        || name.equals("tests")
        || name.equals("docs")
        || name.equals("assets")
        || name.equals("public")) {
      return false;
    }
    return new File(dir, "pom.xml").exists()
        || new File(dir, "package.json").exists()
        || new File(dir, "go.mod").exists()
        || new File(dir, "Cargo.toml").exists()
        || new File(dir, "deps.edn").exists()
        || new File(dir, "build.gradle").exists()
        || new File(dir, "build.gradle.kts").exists()
        || new File(dir, "src/main/java").exists()
        || new File(dir, "src/main").exists();
  }

  public static void addDiscoveredProject(
      File dir, String displayName, List<Map<String, String>> discovered, Set<String> seenPaths) {
    try {
      String canonical = dir.getCanonicalPath();
      if (seenPaths.add(canonical)) {
        discovered.add(Map.of("name", displayName, "path", canonical));
      }
    } catch (IOException e) {
      String abs = dir.getAbsolutePath();
      if (seenPaths.add(abs)) {
        discovered.add(Map.of("name", displayName, "path", abs));
      }
    }
  }

  public static void registerSubProjects(
      File projectDir,
      String projectDisplayName,
      List<Map<String, String>> discovered,
      Set<String> seenPaths) {
    File[] subFiles = projectDir.listFiles();
    if (subFiles == null) {
      return;
    }
    java.util.Arrays.sort(subFiles, java.util.Comparator.comparing(File::getName));
    for (File sub : subFiles) {
      if (isSubProjectDirectory(sub)) {
        addDiscoveredProject(
            sub, projectDisplayName + " / " + sub.getName(), discovered, seenPaths);
      }
    }
  }

  public static void scanDirectoryForProjects(
      File dir,
      String displayPrefix,
      List<Map<String, String>> discovered,
      Set<String> seenPaths,
      Set<String> visitedDirs,
      int depthRemaining) {
    if (dir == null || !dir.exists() || !dir.isDirectory() || depthRemaining < 0) {
      return;
    }

    String canonicalPath;
    try {
      canonicalPath = dir.getCanonicalPath();
    } catch (IOException e) {
      canonicalPath = dir.getAbsolutePath();
    }
    if (!visitedDirs.add(canonicalPath)) {
      return;
    }

    boolean isProject = isProjectDirectory(dir);
    if (isProject) {
      String displayName = displayPrefix.isEmpty() ? dir.getName() : displayPrefix;
      addDiscoveredProject(dir, displayName, discovered, seenPaths);
      registerSubProjects(dir, displayName, discovered, seenPaths);
      return;
    }

    if (depthRemaining == 0) {
      return;
    }

    File[] children = dir.listFiles();
    if (children == null) {
      return;
    }
    java.util.Arrays.sort(children, java.util.Comparator.comparing(File::getName));
    for (File child : children) {
      if (child.isDirectory() && !isIgnoredDirectory(child)) {
        String nextPrefix =
            displayPrefix.isEmpty() ? child.getName() : displayPrefix + " / " + child.getName();
        scanDirectoryForProjects(
            child, nextPrefix, discovered, seenPaths, visitedDirs, depthRemaining - 1);
      }
    }
  }
}
