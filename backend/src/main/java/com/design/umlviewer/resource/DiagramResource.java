package com.design.umlviewer.resource;

import com.design.umlviewer.domain.mailbox.FileMailboxService;
import com.design.umlviewer.domain.mailbox.MailboxEnvelope;
import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.GraphCompiler;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DiagramResource {

  @Inject GraphCompiler graphCompiler;

  @Inject FileMailboxService mailboxService;

  private static final String DEFAULT_PROJECT_ROOT = ".";

  private String normalizeRoot(String root) {
    if (root == null || root.isBlank()) {
      return DEFAULT_PROJECT_ROOT;
    }
    String normalized = root.trim();
    String userHome = System.getProperty("user.home", "");
    String labsPath = userHome + "/workspace/labs";
    if (normalized.equals(".")
        && !new File(".", "pom.xml").exists()
        && new File(labsPath, "archlens/pom.xml").exists()) {
      return labsPath + "/archlens";
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

    java.util.List<Map<String, String>> discovered = new java.util.ArrayList<>();
    discovered.add(current);

    File parent = cwd.getParentFile();
    if (parent != null && parent.exists() && parent.isDirectory()) {
      File[] siblings = parent.listFiles();
      if (siblings != null) {
        java.util.Arrays.sort(siblings, java.util.Comparator.comparing(File::getName));
        for (File sibling : siblings) {
          if (sibling.isDirectory() && !sibling.getName().startsWith(".") && !sibling.equals(cwd)) {
            boolean isProject =
                new File(sibling, "pom.xml").exists()
                    || new File(sibling, "package.json").exists()
                    || new File(sibling, "go.mod").exists()
                    || new File(sibling, "Cargo.toml").exists()
                    || new File(sibling, "deps.edn").exists()
                    || new File(sibling, ".git").exists()
                    || new File(sibling, "src").exists();
            if (isProject) {
              discovered.add(
                  Map.of(
                      "name", sibling.getName(),
                      "path", sibling.getAbsolutePath()));

              File[] subFiles = sibling.listFiles();
              if (subFiles != null) {
                java.util.Arrays.sort(subFiles, java.util.Comparator.comparing(File::getName));
                for (File sub : subFiles) {
                  if (sub.isDirectory() && !sub.getName().startsWith(".")) {
                    boolean isSubProject =
                        new File(sub, "pom.xml").exists()
                            || new File(sub, "package.json").exists()
                            || new File(sub, "src/main/java").exists();
                    if (isSubProject
                        && !sub.getName().equals("target")
                        && !sub.getName().equals("node_modules")
                        && !sub.getName().equals("build")) {
                      discovered.add(
                          Map.of(
                              "name",
                              sibling.getName() + " / " + sub.getName(),
                              "path",
                              sub.getAbsolutePath()));
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    return Map.of(
        "current", current,
        "discovered", discovered);
  }

  @GET
  @Path("/fs/directories")
  public Map<String, Object> listDirectories(@QueryParam("path") String rawPath) {
    File targetDir;
    if (rawPath == null || rawPath.isBlank() || rawPath.trim().equals(".")) {
      try {
        targetDir = new File(".").getCanonicalFile();
      } catch (IOException e) {
        targetDir = new File(".").getAbsoluteFile();
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

    java.util.List<Map<String, String>> breadcrumbs = new java.util.ArrayList<>();
    java.util.List<File> hierarchy = new java.util.ArrayList<>();
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

    java.util.List<Map<String, String>> quickNav = new java.util.ArrayList<>();
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

    java.util.List<Map<String, Object>> directories = new java.util.ArrayList<>();
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
    return response;
  }

  @POST
  @Path("/fs/pick-directory")
  public Map<String, Object> pickDirectory() {
    if (Boolean.getBoolean("java.awt.headless") || Boolean.getBoolean("test.headless")) {
      return Map.of("success", false, "supported", false, "reason", "headless");
    }
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    if (os.contains("mac")) {
      try {
        ProcessBuilder pb =
            new ProcessBuilder(
                "osascript",
                "-e",
                "tell application \"System Events\" to activate",
                "-e",
                "tell application \"System Events\" to return POSIX path of (choose folder with prompt \"Select Repository or Project Directory\")");
        Process p = pb.start();
        boolean finished = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
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
    return Map.of("success", false, "supported", false);
  }

  @GET
  @Path("/graph")
  public ArchitectureGraph getGraph(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return graphCompiler.compileGraph(normalizeRoot(projectRoot), proposalId);
  }

  @GET
  @Path("/policy")
  public ArchitecturePolicy getPolicy(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return graphCompiler.loadPolicy(normalizeRoot(projectRoot));
  }

  @GET
  @Path("/mailbox/to-agent")
  public MailboxEnvelope getToAgentMailbox(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return mailboxService.readMailbox(normalizeRoot(projectRoot), true);
  }

  @POST
  @Path("/mailbox/to-agent")
  public MailboxEnvelope.MailboxCommand sendToAgent(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      Map<String, Object> request)
      throws IOException {
    String op = (String) request.getOrDefault("op", "COMMAND");
    @SuppressWarnings("unchecked")
    Map<String, Object> target = (Map<String, Object>) request.get("target");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) request.get("payload");

    return mailboxService.appendCommand(normalizeRoot(projectRoot), true, op, target, payload);
  }

  @GET
  @Path("/source")
  public Map<String, Object> getSourceCode(
      @QueryParam("filePath") String filePath, @QueryParam("line") @DefaultValue("1") int line) {
    if (filePath == null || filePath.isBlank()) {
      return Map.of("error", "No filePath provided", "content", "");
    }

    File f = new File(filePath);
    // ponytail: resilient path fallback for renamed repository folders or relative paths
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

  @GET
  @Path("/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  public Multi<Map<String, Object>> streamEvents() {
    // SSE heartbeat / live-reload pulse
    return Multi.createFrom()
        .ticks()
        .every(Duration.ofSeconds(2))
        .map(tick -> Map.of("event", "ping", "tick", tick));
  }
}
