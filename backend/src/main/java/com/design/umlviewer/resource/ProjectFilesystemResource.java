package com.design.umlviewer.resource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST endpoint handling project discovery, filesystem directory browsing, native directory picker
 * dialogs, and source code streaming.
 */
@Path("/api")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectFilesystemResource {

  public ProjectFilesystemResource() {}

  @GET
  @Path("/projects")
  public Map<String, Object> listProjects() {
    File cwd;
    try {
      cwd = new File(".").getCanonicalFile();
    } catch (IOException e) {
      cwd = new File(".").getAbsoluteFile();
    }
    String currentName = cwd.getName();

    Map<String, String> current = Map.of("name", currentName + " (Active Workspace)", "path", ".");

    List<Map<String, String>> discovered = new ArrayList<>();
    discovered.add(current);

    Set<String> seenPaths = new HashSet<>();
    try {
      seenPaths.add(cwd.getCanonicalPath());
    } catch (IOException e) {
      seenPaths.add(cwd.getAbsolutePath());
    }

    Set<String> visitedDirs = new HashSet<>();

    File parent = cwd.getParentFile();
    if (parent != null
        && parent.exists()
        && parent.isDirectory()
        && !parent.getAbsolutePath().equals("/")) {
      File[] siblings = parent.listFiles();
      if (siblings != null) {
        java.util.Arrays.sort(siblings, java.util.Comparator.comparing(File::getName));
        for (File sibling : siblings) {
          if (sibling.isDirectory()
              && !WorkspacePathResolver.isIgnoredDirectory(sibling)
              && !sibling.equals(cwd)) {
            WorkspacePathResolver.scanDirectoryForProjects(
                sibling, sibling.getName(), discovered, seenPaths, visitedDirs, 1);
          }
        }
      }
    }

    String containerPath = System.getProperty("archlens.container.workspace", "/workspace");
    File containerWorkspace = new File(containerPath);
    if (containerWorkspace.exists() && containerWorkspace.isDirectory()) {
      WorkspacePathResolver.scanDirectoryForProjects(
          containerWorkspace, "", discovered, seenPaths, visitedDirs, 3);
    }

    return Map.of(
        "current", current,
        "discovered", discovered);
  }

  @GET
  @Path("/fs/directories")
  public Map<String, Object> listDirectories(@QueryParam("path") String rawPath) {
    String containerPath = System.getProperty("archlens.container.workspace", "/workspace");
    File containerWorkspace = new File(containerPath);
    File targetDir;
    if (rawPath == null || rawPath.isBlank() || rawPath.trim().equals(".")) {
      if (WorkspacePathResolver.isContainerEnvironment()
          && containerWorkspace.exists()
          && containerWorkspace.isDirectory()) {
        try {
          targetDir = containerWorkspace.getCanonicalFile();
        } catch (IOException e) {
          targetDir = containerWorkspace.getAbsoluteFile();
        }
      } else {
        try {
          targetDir = new File(".").getCanonicalFile();
        } catch (IOException e) {
          targetDir = new File(".").getAbsoluteFile();
        }
      }
    } else {
      String expanded = rawPath.trim();
      if (expanded.startsWith("~")) {
        expanded = System.getProperty("user.home") + expanded.substring(1);
      }
      targetDir = new File(expanded);
      try {
        targetDir = targetDir.getCanonicalFile();
      } catch (IOException e) {
        targetDir = targetDir.getAbsoluteFile();
      }
    }

    if (!targetDir.exists() || !targetDir.isDirectory()) {
      File userHome = new File(System.getProperty("user.home"));
      if (targetDir.getParentFile() != null && targetDir.getParentFile().exists()) {
        targetDir = targetDir.getParentFile();
      } else if (userHome.exists()) {
        targetDir = userHome;
      }
    }

    String canonicalCurrent = targetDir.getAbsolutePath();
    File parentFile = targetDir.getParentFile();
    String parentPath = parentFile != null ? parentFile.getAbsolutePath() : null;

    List<Map<String, String>> breadcrumbs = new ArrayList<>();
    List<File> hierarchy = new ArrayList<>();
    File cur = targetDir;
    while (cur != null) {
      hierarchy.add(0, cur);
      cur = cur.getParentFile();
    }
    for (File f : hierarchy) {
      String name = f.getName();
      if (name.isEmpty()) {
        name = f.getPath();
      }
      breadcrumbs.add(Map.of("name", name, "path", f.getAbsolutePath()));
    }

    List<Map<String, String>> quickNav = new ArrayList<>();
    String userHome = System.getProperty("user.home");
    quickNav.add(Map.of("name", "Home", "path", userHome, "icon", "home"));
    try {
      quickNav.add(
          Map.of(
              "name",
              "Current Workspace",
              "path",
              new File(".").getCanonicalPath(),
              "icon",
              "briefcase"));
    } catch (IOException ignored) {
    }

    if (containerWorkspace.exists() && containerWorkspace.isDirectory()) {
      String resolvedWorkspacePath;
      try {
        resolvedWorkspacePath = containerWorkspace.getCanonicalPath();
      } catch (IOException e) {
        resolvedWorkspacePath = containerWorkspace.getAbsolutePath();
      }
      quickNav.add(
          Map.of("name", "Mounted Workspace", "path", resolvedWorkspacePath, "icon", "folder-git"));
    }

    File workspaceLabs = new File(userHome, "workspace/labs");
    if (workspaceLabs.exists() && workspaceLabs.isDirectory()) {
      quickNav.add(
          Map.of("name", "Labs", "path", workspaceLabs.getAbsolutePath(), "icon", "folder-git"));
    } else {
      File workspace = new File(userHome, "workspace");
      if (workspace.exists() && workspace.isDirectory()) {
        quickNav.add(
            Map.of("name", "Workspace", "path", workspace.getAbsolutePath(), "icon", "folder-git"));
      }
    }

    List<Map<String, Object>> directories = new ArrayList<>();
    File[] children = targetDir.listFiles(File::isDirectory);
    if (children != null) {
      java.util.Arrays.sort(
          children,
          java.util.Comparator.comparing(f -> f.getName().toLowerCase(java.util.Locale.ROOT)));
      for (File child : children) {
        String name = child.getName();
        if (name.startsWith(".")
            || name.equals("node_modules")
            || name.equals("target")
            || name.equals("build")) {
          continue;
        }
        boolean isMaven = new File(child, "pom.xml").exists();
        boolean isGradle =
            new File(child, "build.gradle").exists()
                || new File(child, "build.gradle.kts").exists();
        boolean isNode = new File(child, "package.json").exists();
        boolean isJava = new File(child, "src/main/java").exists();
        boolean isGit = new File(child, ".git").exists();
        boolean isProject = isMaven || isGradle || isNode || isJava || isGit;

        String projectType = null;
        if (isMaven) {
          projectType = "Maven";
        } else if (isGradle) {
          projectType = "Gradle";
        } else if (isJava) {
          projectType = "Java";
        } else if (isNode) {
          projectType = "Node";
        } else if (isGit) {
          projectType = "Git";
        }

        File[] subDirs = child.listFiles(File::isDirectory);
        boolean hasChildren = subDirs != null && subDirs.length > 0;

        Map<String, Object> dirEntry = new HashMap<>();
        dirEntry.put("name", name);
        dirEntry.put("path", child.getAbsolutePath());
        dirEntry.put("isProject", isProject);
        if (projectType != null) {
          dirEntry.put("projectType", projectType);
        }
        dirEntry.put("hasChildren", hasChildren);
        directories.add(dirEntry);
      }
    }

    Map<String, Object> response = new HashMap<>();
    response.put("currentPath", canonicalCurrent);
    if (parentPath != null) {
      response.put("parentPath", parentPath);
    }
    response.put("breadcrumbs", breadcrumbs);
    response.put("quickNav", quickNav);
    response.put("directories", directories);
    boolean isContainer = WorkspacePathResolver.isContainerEnvironment();
    response.put("isContainer", isContainer);
    boolean isHeadless =
        Boolean.getBoolean("java.awt.headless") || Boolean.getBoolean("test.headless");
    boolean nativePickerSupported = WorkspacePathResolver.isMacOs() && !isHeadless && !isContainer;
    response.put("nativePickerSupported", nativePickerSupported);
    return response;
  }

  @POST
  @Path("/fs/pick-directory")
  public Map<String, Object> pickDirectory() {
    if (Boolean.getBoolean("java.awt.headless") || Boolean.getBoolean("test.headless")) {
      return Map.of("success", false, "supported", false, "reason", "headless");
    }
    if (WorkspacePathResolver.isContainerEnvironment()) {
      return Map.of("success", false, "supported", false, "reason", "container");
    }
    if (WorkspacePathResolver.isMacOs()) {
      try {
        ProcessBuilder pb =
            new ProcessBuilder(
                "osascript",
                "-e",
                "tell application \"System Events\" to activate",
                "-e",
                "tell application \"System Events\" to return POSIX path of (choose folder with prompt \"Select Repository or Project Directory\")");
        Process p = pb.start();
        boolean finished = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
        if (finished && p.exitValue() == 0) {
          String selectedPath =
              new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                  .trim();
          if (!selectedPath.isEmpty()) {
            return Map.of("success", true, "path", selectedPath);
          }
        }
        if (!finished) {
          p.destroyForcibly();
        }
        return Map.of("success", false, "cancelled", true);
      } catch (IOException | InterruptedException e) {
        return Map.of("success", false, "error", e.getMessage());
      }
    }
    return Map.of("success", false, "supported", false, "reason", "unsupported_os");
  }

  public Map<String, Object> getSourceCode(String filePath, int line) {
    return getSourceCode(filePath, line, null);
  }

  @GET
  @Path("/source")
  public Map<String, Object> getSourceCode(
      @QueryParam("filePath") String filePath,
      @QueryParam("line") @DefaultValue("1") int line,
      @QueryParam("projectRoot") String projectRoot) {
    if (filePath == null || filePath.isBlank()) {
      return Map.of("error", "No filePath provided", "content", "");
    }

    File f = new File(filePath);
    // ponytail: resilient path fallback for external project roots and relative paths
    if (!f.exists() || !f.isFile()) {
      if (projectRoot != null && !projectRoot.isBlank()) {
        File projectFile = new File(projectRoot, filePath);
        if (projectFile.exists() && projectFile.isFile()) {
          f = projectFile;
        }
      }
      if (!f.exists() || !f.isFile()) {
        if (filePath.startsWith("/workspace/labs/")) {
          File hostFallback =
              new File(filePath.replace("/workspace/labs", "/Users/fady/workspace/labs"));
          if (hostFallback.exists() && hostFallback.isFile()) {
            f = hostFallback;
          }
        }
        if (filePath.contains("agentlens")) {
          File fallback = new File(filePath.replace("agentlens", "archlens"));
          if (fallback.exists() && fallback.isFile()) {
            f = fallback;
          }
        }
        if (filePath.contains("unclebob-design")) {
          File fallback = new File(filePath.replace("unclebob-design", "archlens"));
          if (fallback.exists() && fallback.isFile()) {
            f = fallback;
          }
        }
        if (!f.exists() || !f.isFile()) {
          File rel = new File(".", filePath);
          if (rel.exists() && rel.isFile()) {
            f = rel;
          }
        }
        if (!f.exists() || !f.isFile()) {
          File parentRel = new File("..", filePath);
          if (parentRel.exists() && parentRel.isFile()) {
            f = parentRel;
          }
        }
      }
    }

    if (!f.exists() || !f.isFile()) {
      return Map.of(
          "error", "File not found: " + filePath,
          "filePath", filePath,
          "content", "// File could not be loaded on server: " + filePath);
    }

    try {
      String content = Files.readString(f.toPath());
      return Map.of(
          "fileName",
          f.getName(),
          "filePath",
          f.getAbsolutePath(),
          "content",
          content,
          "targetLine",
          line);
    } catch (Exception e) {
      return Map.of(
          "error", "Failed to read file: " + e.getMessage(),
          "filePath", filePath,
          "content", "// Error reading file: " + e.getMessage());
    }
  }
}
