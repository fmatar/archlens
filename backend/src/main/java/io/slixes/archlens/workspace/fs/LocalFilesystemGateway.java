package io.slixes.archlens.workspace.fs;

import io.slixes.archlens.usecase.FilesystemGateway;
import io.slixes.archlens.usecase.ProjectPathGuard;
import io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase;
import io.slixes.archlens.usecase.WorkspacePathResolver;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Ring 2 filesystem adapter implementing {@link FilesystemGateway} for project discovery, directory
 * browsing, native OS folder dialogs, confined source reading, and transactional snapshot
 * backup/restore.
 */
@ApplicationScoped
public class LocalFilesystemGateway implements FilesystemGateway {

  @FunctionalInterface
  public interface NativePickerRunner {
    Map<String, Object> runMacPicker();
  }

  private static final String SNAPSHOTS_DIR = ".archlens/snapshots";
  private static final String TRASH_DIR = ".archlens/trash";
  private static final String MANIFEST_FILE = "manifest.txt";
  private static final String FILES_BACKUP_DIR = "files";

  private static final Set<String> IGNORED_DIRS =
      Set.of(
          ".git",
          ".archlens",
          ".agent",
          ".agents",
          ".github",
          ".claude",
          ".cursor",
          ".vscode",
          "node_modules",
          "target",
          "dist",
          "build",
          "out",
          "coverage",
          "__pycache__",
          ".venv",
          "venv",
          ".idea",
          ".settings",
          ".gradle",
          ".next",
          ".svelte-kit");

  private static final Set<String> SOURCE_EXTENSIONS =
      Set.of(
          ".java", ".py", ".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs", ".svelte", ".go", ".rs",
          ".kt");

  private final NativePickerRunner pickerRunner;

  public LocalFilesystemGateway() {
    this(LocalFilesystemGateway::invokeOsascriptPicker);
  }

  LocalFilesystemGateway(NativePickerRunner pickerRunner) {
    this.pickerRunner =
        pickerRunner != null ? pickerRunner : LocalFilesystemGateway::invokeOsascriptPicker;
  }

  @Override
  public Map<String, Object> listProjects() {
    File cwd = canonicalOrAbsolute(new File("."));
    String currentName = cwd.getName();
    Map<String, String> current = Map.of("name", currentName + " (Active Workspace)", "path", ".");

    List<Map<String, String>> discovered = new ArrayList<>();
    discovered.add(current);

    Set<String> seenPaths = new HashSet<>();
    seenPaths.add(cwd.getAbsolutePath());

    Set<String> visitedDirs = new HashSet<>();
    File parent = cwd.getParentFile();
    if (parent != null && parent.isDirectory() && !parent.getAbsolutePath().equals("/")) {
      if (WorkspacePathResolver.isProjectDirectory(parent)) {
        WorkspacePathResolver.addDiscoveredProject(parent, parent.getName(), discovered, seenPaths);
      }
      File[] siblings = parent.listFiles();
      if (siblings != null) {
        Arrays.sort(siblings, Comparator.comparing(File::getName));
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
    if (containerWorkspace.isDirectory()) {
      WorkspacePathResolver.scanDirectoryForProjects(
          containerWorkspace, "", discovered, seenPaths, visitedDirs, 3);
    }

    return Map.of("current", current, "discovered", discovered);
  }

  public List<Path> resolveAllowedBrowseRoots(List<Path> configuredAllowedRoots) {
    Set<Path> roots = new LinkedHashSet<>();
    if (configuredAllowedRoots != null) {
      for (Path p : configuredAllowedRoots) {
        addCanonicalDir(roots, p);
      }
      return List.copyOf(roots);
    }
    String userHome = System.getProperty("user.home");
    if (userHome != null && !userHome.isBlank()) {
      addCanonicalDir(roots, Path.of(userHome));
    }
    addCanonicalDir(roots, Path.of("."));
    String containerPath = System.getProperty("archlens.container.workspace", "/workspace");
    if (containerPath != null && !containerPath.isBlank()) {
      addCanonicalDir(roots, Path.of(containerPath));
    }
    addRootsFromSpec(roots, System.getProperty("archlens.allowed.roots"));
    addRootsFromSpec(roots, System.getenv("ARCHLENS_ALLOWED_ROOTS"));
    return List.copyOf(roots);
  }

  static void addRootsFromSpec(Set<Path> roots, String spec) {
    if (spec == null || spec.isBlank()) {
      return;
    }
    for (String entry : spec.split(Pattern.quote(File.pathSeparator))) {
      String trimmed = entry.trim();
      if (!trimmed.isEmpty()) {
        addCanonicalDir(roots, Path.of(trimmed));
      }
    }
  }

  static void addCanonicalDir(Set<Path> roots, Path candidate) {
    if (candidate == null) {
      return;
    }
    try {
      Path real = candidate.toAbsolutePath().normalize().toRealPath();
      if (Files.isDirectory(real)) {
        roots.add(real);
      }
    } catch (IOException ignored) {
      // Root doesn't exist on disk; skip
    }
  }

  public static boolean isWithinAllowedRoots(Path candidate, List<Path> allowedRoots) {
    if (candidate == null || allowedRoots == null || allowedRoots.isEmpty()) {
      return false;
    }
    Path normalized = candidate.toAbsolutePath().normalize();
    for (Path allowed : allowedRoots) {
      if (normalized.startsWith(allowed)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Map<String, Object> listDirectories(String rawPath, List<Path> configuredAllowedRoots) {
    String containerPath = System.getProperty("archlens.container.workspace", "/workspace");
    File containerWorkspace = new File(containerPath);
    List<Path> allowedRoots = resolveAllowedBrowseRoots(configuredAllowedRoots);
    boolean explicitPath = rawPath != null && !rawPath.isBlank() && !rawPath.trim().equals(".");
    File targetDir;
    if (!explicitPath) {
      if (WorkspacePathResolver.isContainerEnvironment() && containerWorkspace.isDirectory()) {
        targetDir = canonicalOrAbsolute(containerWorkspace);
      } else {
        targetDir = canonicalOrAbsolute(new File("."));
      }
    } else {
      String expanded = rawPath.trim();
      if (expanded.equals("~") || expanded.startsWith("~/") || expanded.startsWith("~\\")) {
        expanded = System.getProperty("user.home") + expanded.substring(1);
      }
      targetDir = canonicalOrAbsolute(new File(expanded));
      if (!isWithinAllowedRoots(targetDir.toPath(), allowedRoots)) {
        return Map.of(
            "error",
            "Access denied: directory is outside allowed browse roots",
            "denied",
            true,
            "path",
            rawPath);
      }
    }

    if (!targetDir.isDirectory()) {
      File userHomeDir = new File(System.getProperty("user.home"));
      File parent = targetDir.getParentFile();
      if (parent != null
          && parent.isDirectory()
          && isWithinAllowedRoots(parent.toPath(), allowedRoots)) {
        targetDir = parent;
      } else if (userHomeDir.isDirectory()
          && isWithinAllowedRoots(userHomeDir.toPath(), allowedRoots)) {
        targetDir = userHomeDir;
      } else if (!allowedRoots.isEmpty()) {
        targetDir = allowedRoots.get(0).toFile();
      }
    }

    String canonicalCurrent = targetDir.getAbsolutePath();
    File parentFile = targetDir.getParentFile();
    String parentPath =
        parentFile != null && isWithinAllowedRoots(parentFile.toPath(), allowedRoots)
            ? parentFile.getAbsolutePath()
            : null;

    List<Map<String, String>> breadcrumbs = buildBreadcrumbs(targetDir, allowedRoots);
    List<Map<String, String>> quickNav = buildQuickNav(containerWorkspace);
    List<Map<String, Object>> directories = buildChildDirectoryEntries(targetDir);

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
    response.put(
        "nativePickerSupported", WorkspacePathResolver.isMacOs() && !isHeadless && !isContainer);
    return response;
  }

  private static File canonicalOrAbsolute(File file) {
    try {
      return file.getCanonicalFile();
    } catch (IOException e) {
      return file.getAbsoluteFile();
    }
  }

  private static List<Map<String, String>> buildBreadcrumbs(
      File targetDir, List<Path> allowedRoots) {
    List<Map<String, String>> breadcrumbs = new ArrayList<>();
    List<File> hierarchy = new ArrayList<>();
    for (File cur = targetDir; cur != null; cur = cur.getParentFile()) {
      hierarchy.add(0, cur);
    }
    for (File f : hierarchy) {
      if (!isWithinAllowedRoots(f.toPath(), allowedRoots)) {
        continue;
      }
      String name = f.getName().isEmpty() ? f.getPath() : f.getName();
      breadcrumbs.add(Map.of("name", name, "path", f.getAbsolutePath()));
    }
    return breadcrumbs;
  }

  private static List<Map<String, String>> buildQuickNav(File containerWorkspace) {
    List<Map<String, String>> quickNav = new ArrayList<>();
    String userHome = System.getProperty("user.home");
    quickNav.add(Map.of("name", "Home", "path", userHome, "icon", "home"));
    quickNav.add(
        Map.of(
            "name",
            "Current Workspace",
            "path",
            canonicalOrAbsolute(new File(".")).getAbsolutePath(),
            "icon",
            "briefcase"));

    if (containerWorkspace.isDirectory()) {
      quickNav.add(
          Map.of(
              "name",
              "Mounted Workspace",
              "path",
              canonicalOrAbsolute(containerWorkspace).getAbsolutePath(),
              "icon",
              "folder-git"));
    }

    File workspace = new File(userHome, "workspace");
    if (workspace.isDirectory()) {
      quickNav.add(
          Map.of("name", "Workspace", "path", workspace.getAbsolutePath(), "icon", "folder-git"));
    }
    return quickNav;
  }

  private static List<Map<String, Object>> buildChildDirectoryEntries(File targetDir) {
    List<Map<String, Object>> directories = new ArrayList<>();
    File[] children = targetDir.listFiles(File::isDirectory);
    if (children == null) {
      return directories;
    }
    Arrays.sort(children, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
    for (File child : children) {
      String name = child.getName();
      if (name.startsWith(".")
          || name.equals("node_modules")
          || name.equals("target")
          || name.equals("build")) {
        continue;
      }
      String projectType = detectProjectType(child);
      File[] subDirs = child.listFiles(File::isDirectory);
      Map<String, Object> dirEntry = new HashMap<>();
      dirEntry.put("name", name);
      dirEntry.put("path", child.getAbsolutePath());
      dirEntry.put("isProject", projectType != null);
      if (projectType != null) {
        dirEntry.put("projectType", projectType);
      }
      dirEntry.put("hasChildren", subDirs != null && subDirs.length > 0);
      directories.add(dirEntry);
    }
    return directories;
  }

  private static String detectProjectType(File child) {
    if (new File(child, "pom.xml").exists()) {
      return "Maven";
    }
    if (new File(child, "build.gradle").exists() || new File(child, "build.gradle.kts").exists()) {
      return "Gradle";
    }
    if (new File(child, "src/main/java").exists()) {
      return "Java";
    }
    if (new File(child, "package.json").exists()) {
      return "Node";
    }
    if (new File(child, ".git").exists()) {
      return "Git";
    }
    return null;
  }

  @Override
  public Map<String, Object> pickDirectory() {
    if (Boolean.getBoolean("java.awt.headless") || Boolean.getBoolean("test.headless")) {
      return Map.of("success", false, "supported", false, "reason", "headless");
    }
    if (WorkspacePathResolver.isContainerEnvironment()) {
      return Map.of("success", false, "supported", false, "reason", "container");
    }
    if (WorkspacePathResolver.isMacOs()) {
      return pickerRunner.runMacPicker();
    }
    return Map.of("success", false, "supported", false, "reason", "unsupported_os");
  }

  private static Map<String, Object> invokeOsascriptPicker() {
    try {
      ProcessBuilder pb =
          new ProcessBuilder(
              "osascript",
              "-e",
              "tell application \"System Events\" to activate",
              "-e",
              "tell application \"System Events\" to return POSIX path of (choose folder with prompt \"Select Repository or Project Directory\")");
      Process p = pb.start();
      boolean finished = p.waitFor(3, TimeUnit.SECONDS);
      if (finished && p.exitValue() == 0) {
        String selectedPath =
            new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!selectedPath.isEmpty()) {
          return Map.of("success", true, "path", selectedPath);
        }
      }
      if (!finished) {
        p.destroyForcibly();
      }
      return Map.of("success", false, "cancelled", true);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Map.of("success", false, "error", e.getMessage());
    }
  }

  @Override
  public Map<String, Object> readConfinedSourceCode(String filePath, int line, String projectRoot) {
    Path root;
    try {
      root = ProjectPathGuard.canonicalRoot(WorkspacePathResolver.normalizeRoot(projectRoot));
    } catch (IOException e) {
      return Map.of(
          "error", "Project root not found",
          "filePath", filePath,
          "content", "// Project root could not be resolved on server");
    }

    ProjectPathGuard.Resolution resolution = ProjectPathGuard.resolveFileWithinRoot(root, filePath);
    if (resolution.status() == ProjectPathGuard.Status.OUTSIDE_ROOT
        || resolution.status() == ProjectPathGuard.Status.INVALID) {
      return Map.of(
          "error",
          "Access denied: path is outside the project root",
          "denied",
          true,
          "filePath",
          filePath,
          "content",
          "// Access denied: " + filePath + " is outside the opened project");
    }
    if (!resolution.isOk()) {
      return Map.of(
          "error", "File not found: " + filePath,
          "filePath", filePath,
          "content", "// File could not be loaded on server: " + filePath);
    }

    Path file = resolution.path();
    try {
      String content = Files.readString(file);
      Path fileName = file.getFileName();
      return Map.of(
          "fileName",
          fileName != null ? fileName.toString() : file.toString(),
          "filePath",
          file.toString(),
          "content",
          content,
          "targetLine",
          line);
    } catch (IOException e) {
      return Map.of(
          "error", "Failed to read file: " + e.getMessage(),
          "filePath", filePath,
          "content", "// Error reading file: " + e.getMessage());
    }
  }

  @Override
  public String createPreRefactorSnapshot(String projectRoot) throws IOException {
    String snapshotId =
        "snap-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    Path rootPath =
        ProjectPathGuard.canonicalRoot(
            projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    Path snapPath = rootPath.resolve(SNAPSHOTS_DIR).resolve(snapshotId);
    ensureNoSymlinkOnPath(rootPath, snapPath);
    Files.createDirectories(snapPath);

    Path policy = rootPath.resolve(".archlens").resolve("policy.json");
    ensureNoSymlinkOnPath(rootPath, policy);
    if (Files.isRegularFile(policy, LinkOption.NOFOLLOW_LINKS)) {
      Files.copy(policy, snapPath.resolve("policy.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    Path filesBackupRoot = snapPath.resolve(FILES_BACKUP_DIR);
    Files.createDirectories(filesBackupRoot);
    List<String> trackedFiles = collectSourceFiles(rootPath);
    for (String relPath : trackedFiles) {
      Path sourceFile = rootPath.resolve(relPath);
      Path backupDest = filesBackupRoot.resolve(relPath);
      Path backupParent = backupDest.getParent();
      if (backupParent != null) {
        Files.createDirectories(backupParent);
      }
      Files.copy(sourceFile, backupDest, StandardCopyOption.REPLACE_EXISTING);
    }
    Files.write(snapPath.resolve(MANIFEST_FILE), trackedFiles, StandardCharsets.UTF_8);
    return snapshotId;
  }

  @Override
  public VerifyAndRollbackRefactorUseCase.RollbackResult restoreSnapshot(
      String projectRoot, String snapshotId) throws IOException {
    if (snapshotId == null || snapshotId.isBlank()) {
      return new VerifyAndRollbackRefactorUseCase.RollbackResult(false, "", List.of());
    }
    if (!ProjectPathGuard.isValidSnapshotId(snapshotId)) {
      throw new IllegalArgumentException("Invalid snapshot id: " + snapshotId);
    }
    Path rootPath =
        ProjectPathGuard.canonicalRoot(
            projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    Path snapshotsBase = rootPath.resolve(SNAPSHOTS_DIR);
    Path snapPath = snapshotsBase.resolve(snapshotId);
    if (!Files.isDirectory(snapPath, LinkOption.NOFOLLOW_LINKS)
        || !snapPath.toRealPath().startsWith(snapshotsBase.toRealPath())) {
      return new VerifyAndRollbackRefactorUseCase.RollbackResult(false, "", List.of());
    }

    Path manifestPath = snapPath.resolve(MANIFEST_FILE);
    Path filesBackupRoot = snapPath.resolve(FILES_BACKUP_DIR);
    boolean hasFileBackup =
        Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)
            && Files.isDirectory(filesBackupRoot, LinkOption.NOFOLLOW_LINKS);
    Set<String> originalFiles = new HashSet<>();
    if (hasFileBackup) {
      for (String line : Files.readAllLines(manifestPath, StandardCharsets.UTF_8)) {
        String relPath = line.trim();
        if (relPath.isEmpty()) {
          continue;
        }
        if (!ProjectPathGuard.isSafeRelativePath(rootPath, relPath)
            || !ProjectPathGuard.isSafeRelativePath(filesBackupRoot, relPath)) {
          throw new IllegalArgumentException(
              "Snapshot manifest contains an unsafe path; rollback aborted: " + relPath);
        }
        originalFiles.add(relPath);
      }
    }

    Path trashSnapRoot = rootPath.resolve(TRASH_DIR).resolve(snapshotId);
    ensureNoSymlinkOnPath(rootPath, trashSnapRoot);

    Path backupPolicy = snapPath.resolve("policy.json");
    if (Files.isRegularFile(backupPolicy, LinkOption.NOFOLLOW_LINKS)) {
      Path archlensDir = rootPath.resolve(".archlens");
      Path targetPolicy = archlensDir.resolve("policy.json");
      ensureNoSymlinkOnPath(rootPath, targetPolicy);
      Files.createDirectories(archlensDir);
      Files.copy(backupPolicy, targetPolicy, StandardCopyOption.REPLACE_EXISTING);
    }

    List<String> trashedFiles = new ArrayList<>();
    if (hasFileBackup) {
      for (String relPath : originalFiles) {
        Path backupFile = filesBackupRoot.resolve(relPath).normalize();
        Path targetFile = rootPath.resolve(relPath).normalize();
        ensureNoSymlinkOnPath(filesBackupRoot, backupFile);
        ensureNoSymlinkOnPath(rootPath, targetFile);
        if (Files.isRegularFile(backupFile, LinkOption.NOFOLLOW_LINKS)) {
          Path targetParent = targetFile.getParent();
          if (targetParent != null) {
            Files.createDirectories(targetParent);
          }
          Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
      }

      List<String> currentSourceFiles = collectSourceFiles(rootPath);
      for (String currentRel : currentSourceFiles) {
        if (!originalFiles.contains(currentRel)) {
          Path sourceFile = rootPath.resolve(currentRel).normalize();
          if (Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) {
            Path trashTarget = trashSnapRoot.resolve(currentRel).normalize();
            ensureNoSymlinkOnPath(rootPath, trashTarget);
            Files.createDirectories(Objects.requireNonNull(trashTarget.getParent()));
            Files.move(sourceFile, trashTarget, StandardCopyOption.REPLACE_EXISTING);
            trashedFiles.add(currentRel);
          }
        }
      }
    }

    String trashRelDir = trashedFiles.isEmpty() ? "" : TRASH_DIR + "/" + snapshotId;
    return new VerifyAndRollbackRefactorUseCase.RollbackResult(
        true, trashRelDir, List.copyOf(trashedFiles));
  }

  private static void ensureNoSymlinkOnPath(Path rootPath, Path target) {
    for (Path p = target; !p.equals(rootPath); p = Objects.requireNonNull(p.getParent())) {
      if (Files.isSymbolicLink(p)) {
        throw new IllegalArgumentException(
            "Snapshot or rollback path must not traverse a symbolic link");
      }
    }
  }

  private List<String> collectSourceFiles(Path rootPath) throws IOException {
    List<String> result = new ArrayList<>();
    if (!Files.exists(rootPath)) {
      return result;
    }
    Files.walkFileTree(
        rootPath,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (!dir.equals(rootPath)) {
              Path fileName = dir.getFileName();
              if (fileName != null && IGNORED_DIRS.contains(fileName.toString())) {
                return FileVisitResult.SKIP_SUBTREE;
              }
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile() && isSourceFile(file)) {
              String rel = rootPath.relativize(file).toString().replace('\\', '/');
              result.add(rel);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
          }
        });
    return result;
  }

  private static boolean isSourceFile(Path file) {
    Path namePath = file.getFileName();
    if (namePath == null) {
      return false;
    }
    String name = namePath.toString();
    int dotIdx = name.lastIndexOf('.');
    return dotIdx >= 0 && SOURCE_EXTENSIONS.contains(name.substring(dotIdx));
  }
}
