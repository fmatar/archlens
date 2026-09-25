package com.design.umlviewer.resource;

import com.design.umlviewer.domain.dossier.ArchitecturalDossierGenerator;
import com.design.umlviewer.domain.mailbox.FileMailboxService;
import com.design.umlviewer.domain.mailbox.MailboxEnvelope;
import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.GraphCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DiagramResource {

  @Inject GraphCompiler graphCompiler;

  @Inject FileMailboxService mailboxService;

  @Inject ArchitecturalDossierGenerator dossierGenerator;

  @Inject ObjectMapper mapper = new ObjectMapper();

  public DiagramResource() {}

  DiagramResource(
      GraphCompiler graphCompiler,
      FileMailboxService mailboxService,
      ArchitecturalDossierGenerator dossierGenerator,
      ObjectMapper mapper) {
    this.graphCompiler = graphCompiler;
    this.mailboxService = mailboxService;
    this.dossierGenerator = dossierGenerator;
    this.mapper = mapper != null ? mapper : new ObjectMapper();
  }

  private static final String DEFAULT_PROJECT_ROOT = ".";

  private boolean isContainerEnvironment() {
    boolean isExplicitTest = System.getProperty("archlens.container.workspace") != null;
    boolean isContainerRuntime =
        new File("/work/quarkus-run.jar").exists()
            || new File("/work/application").exists()
            || new File("/.dockerenv").exists();
    return isExplicitTest || isContainerRuntime || !new File(".", "pom.xml").exists();
  }

  String normalizeRoot(String root) {
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

  private boolean isProjectDirectory(File dir) {
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

  private boolean isIgnoredDirectory(File dir) {
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

  private boolean isSubProjectDirectory(File dir) {
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

  private void addDiscoveredProject(
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

  private void registerSubProjects(
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

  private void scanDirectoryForProjects(
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
          if (sibling.isDirectory() && !isIgnoredDirectory(sibling) && !sibling.equals(cwd)) {
            scanDirectoryForProjects(
                sibling, sibling.getName(), discovered, seenPaths, visitedDirs, 1);
          }
        }
      }
    }

    String containerPath = System.getProperty("archlens.container.workspace", "/workspace");
    File containerWorkspace = new File(containerPath);
    if (containerWorkspace.exists() && containerWorkspace.isDirectory()) {
      scanDirectoryForProjects(containerWorkspace, "", discovered, seenPaths, visitedDirs, 3);
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
      if (isContainerEnvironment()
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
    boolean isContainer = isContainerEnvironment();
    response.put("isContainer", isContainer);
    boolean isHeadless =
        Boolean.getBoolean("java.awt.headless") || Boolean.getBoolean("test.headless");
    boolean nativePickerSupported = isMacOs() && !isHeadless && !isContainer;
    response.put("nativePickerSupported", nativePickerSupported);
    return response;
  }

  private boolean isMacOs() {
    String os = System.getProperty("os.name", "");
    return os.contains("Mac")
        || os.contains("mac")
        || os.contains("Darwin")
        || os.contains("darwin");
  }

  @POST
  @Path("/fs/pick-directory")
  public Map<String, Object> pickDirectory() {
    if (Boolean.getBoolean("java.awt.headless") || Boolean.getBoolean("test.headless")) {
      return Map.of("success", false, "supported", false, "reason", "headless");
    }
    if (isContainerEnvironment()) {
      return Map.of("success", false, "supported", false, "reason", "container");
    }
    if (isMacOs()) {
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

  @GET
  @Path("/graph")
  public ArchitectureGraph getGraph(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return graphCompiler.compileGraph(normalizeRoot(projectRoot), proposalId);
  }

  @GET
  @Path("/snapshots")
  public Map<String, Object> listSnapshots(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    String root = normalizeRoot(projectRoot);
    File snapshotsDir = new File(root, ".archlens/snapshots");
    List<Map<String, Object>> snapshots = new ArrayList<>();

    if (snapshotsDir.exists() && snapshotsDir.isDirectory()) {
      File[] files = snapshotsDir.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null) {
        java.util.Arrays.sort(
            files, java.util.Comparator.comparingLong(File::lastModified).reversed());
        for (File f : files) {
          String name = f.getName();
          String id = name.substring(0, name.length() - 5);
          snapshots.add(
              Map.of(
                  "id",
                  id,
                  "label",
                  id,
                  "tag",
                  id,
                  "date",
                  new java.text.SimpleDateFormat("yyyy-MM-dd")
                      .format(new java.util.Date(f.lastModified()))));
        }
      }
    }

    if (snapshots.isEmpty()) {
      File gitDir = new File(root, ".git");
      if (gitDir.exists()) {
        try {
          Process process =
              new ProcessBuilder("git", "tag", "-l", "--sort=-creatordate")
                  .directory(new File(root))
                  .start();
          try (java.io.BufferedReader reader =
              new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 10) {
              String tag = line.trim();
              if (!tag.isEmpty()) {
                snapshots.add(Map.of("id", tag, "label", tag, "tag", tag));
                count++;
              }
            }
          }
          boolean finished = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
          if (!finished) {
            process.destroyForcibly();
          }
        } catch (Exception ignored) {
          // Gracefully continue with available list
        }
      }
    }

    return Map.of("snapshots", snapshots);
  }

  @GET
  @Path("/snapshots/{snapshotId}")
  public ArchitectureGraph getSnapshot(
      @PathParam("snapshotId") String snapshotId,
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot)
      throws IOException {
    if (snapshotId == null
        || snapshotId.isBlank()
        || snapshotId.contains("..")
        || snapshotId.contains("/")
        || snapshotId.contains("\\")) {
      throw new jakarta.ws.rs.BadRequestException(
          "Invalid snapshot ID: path traversal characters are forbidden.");
    }
    String root = normalizeRoot(projectRoot);
    File baseDir = new File(root, ".archlens");
    File snapshotFile = new File(root, ".archlens/snapshots/" + snapshotId + ".json");
    if (snapshotFile.exists() && snapshotFile.isFile()) {
      if (!snapshotFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
        throw new jakarta.ws.rs.BadRequestException("Invalid snapshot path traversal.");
      }
      return mapper.readValue(snapshotFile, ArchitectureGraph.class);
    }

    File cacheFile = new File(root, ".archlens/cache/" + snapshotId + ".json");
    if (cacheFile.exists() && cacheFile.isFile()) {
      if (!cacheFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
        throw new jakarta.ws.rs.BadRequestException("Invalid snapshot path traversal.");
      }
      return mapper.readValue(cacheFile, ArchitectureGraph.class);
    }

    return graphCompiler.compileGraph(root, null);
  }

  @GET
  @Path("/diagram/llm-dossier")
  @Produces(MediaType.TEXT_PLAIN)
  public String getLlmDossier(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    String normalized = normalizeRoot(projectRoot);
    ArchitectureGraph graph = graphCompiler.compileGraph(normalized, proposalId);
    ArchitecturePolicy policy = graphCompiler.loadPolicy(normalized);
    return dossierGenerator.generate(graph, policy);
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
