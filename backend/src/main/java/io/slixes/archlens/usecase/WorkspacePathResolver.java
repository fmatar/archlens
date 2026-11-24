package io.slixes.archlens.usecase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Level 1 Application utility resolving filesystem paths, container environments, and project
 * hierarchies for the Archlens workbench.
 */
public final class WorkspacePathResolver {

  public static final String DEFAULT_PROJECT_ROOT = ".";

  private static final Pattern HOST_USER_WORKSPACE_PATTERN =
      Pattern.compile("^/(?:Users|home)/[^/]+/workspace(?:/(.*))?$");

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
        && !new File(".", ".archlens/policy.json").exists()
        && new File("..", ".archlens/policy.json").exists()) {
      try {
        return new File("..").getCanonicalPath();
      } catch (IOException e) {
        return new File("..").getAbsolutePath();
      }
    }
    // Expand home directory shorthand ~
    if (normalized.startsWith("~")) {
      normalized = userHome + normalized.substring(1);
    }
    // In container environments, map host workspace paths to container mounts
    if (isContainerEnvironment()
        && containerWorkspace.exists()
        && containerWorkspace.isDirectory()) {
      normalized = mapHostWorkspaceToContainer(normalized, userHome, containerPath);
    }
    return normalized;
  }

  private static String mapHostWorkspaceToContainer(
      String normalized, String userHome, String containerPath) {
    String lexNormalized = lexicalNormalize(normalized);
    String cleanContainer = stripTrailingSlash(lexicalNormalize(containerPath));

    String explicitHost =
        System.getProperty("archlens.host.workspace", System.getenv("ARCHLENS_HOST_WORKSPACE"));
    if (explicitHost != null && !explicitHost.isBlank()) {
      String cleanHost = stripTrailingSlash(lexicalNormalize(explicitHost.trim()));
      String mapped = rewritePrefixWithinContainer(lexNormalized, cleanHost, cleanContainer);
      return mapped != null ? mapped : lexNormalized;
    }

    if (!userHome.isBlank()) {
      String cleanHomeWs = stripTrailingSlash(lexicalNormalize(userHome.trim() + "/workspace"));
      String mapped = rewritePrefixWithinContainer(lexNormalized, cleanHomeWs, cleanContainer);
      if (mapped != null) {
        return mapped;
      }
    }

    Matcher matcher = HOST_USER_WORKSPACE_PATTERN.matcher(lexNormalized);
    if (matcher.matches()) {
      String suffix = matcher.group(1);
      String candidate =
          (suffix == null || suffix.isEmpty()) ? cleanContainer : cleanContainer + "/" + suffix;
      String normCandidate = Path.of(candidate).normalize().toString();
      if (normCandidate.equals(cleanContainer) || normCandidate.startsWith(cleanContainer + "/")) {
        return normCandidate;
      }
    }
    return lexNormalized;
  }

  private static String lexicalNormalize(String path) {
    if (path != null && path.startsWith("/")) {
      return Path.of(path).normalize().toString();
    }
    return path;
  }

  private static String stripTrailingSlash(String path) {
    int end = path.length();
    while (end > 1 && path.charAt(end - 1) == '/') {
      end--;
    }
    return end == path.length() ? path : path.substring(0, end);
  }

  private static String rewritePrefixWithinContainer(
      String path, String hostPrefix, String cleanContainer) {
    String candidate;
    if (path.equals(hostPrefix)) {
      candidate = cleanContainer;
    } else if (path.startsWith(hostPrefix + "/")) {
      candidate = cleanContainer + path.substring(hostPrefix.length());
    } else {
      return null;
    }
    String normCandidate = Path.of(candidate).normalize().toString();
    if (normCandidate.equals(cleanContainer) || normCandidate.startsWith(cleanContainer + "/")) {
      return normCandidate;
    }
    return null;
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
