package io.slixes.archlens.usecase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Level 1 Application guard confining filesystem access to a project root. Every path is
 * canonicalized (symlinks resolved, {@code ..} segments removed) before it is compared with the
 * canonical project root, so neither relative escapes nor symlinks can reach files outside the
 * project.
 */
public final class ProjectPathGuard {

  /** Snapshot identifiers are generated as {@code snap-<epochMillis>-<8 hex chars>}. */
  private static final Pattern SNAPSHOT_ID_PATTERN =
      Pattern.compile("^snap-[0-9]{1,19}-[0-9a-f]{8}$");

  /** Outcome of resolving a requested file against a project root. */
  public enum Status {
    OK,
    NOT_FOUND,
    OUTSIDE_ROOT,
    INVALID
  }

  /** Resolution result carrying the canonical file path when {@link Status#OK}. */
  public record Resolution(Status status, Path path) {
    public boolean isOk() {
      return status == Status.OK;
    }
  }

  private ProjectPathGuard() {
    // Utility class
  }

  /**
   * Returns the canonical (real) path of an existing project root directory.
   *
   * @throws IOException when the root does not exist or is not a directory
   */
  public static Path canonicalRoot(String projectRoot) throws IOException {
    String raw = projectRoot == null || projectRoot.isBlank() ? "." : projectRoot.trim();
    Path root;
    try {
      root = Paths.get(raw).toAbsolutePath().normalize().toRealPath();
    } catch (InvalidPathException e) {
      throw new IOException("Invalid project root: " + raw, e);
    }
    if (!Files.isDirectory(root)) {
      throw new IOException("Project root is not a directory: " + raw);
    }
    return root;
  }

  /** Canonical project root, or empty when it cannot be resolved. */
  public static Optional<Path> tryCanonicalRoot(String projectRoot) {
    try {
      return Optional.of(canonicalRoot(projectRoot));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /**
   * Resolves {@code requested} (absolute, or relative to the root) to a regular file contained in
   * the canonical {@code root}. Paths outside the root are rejected before their existence is
   * reported, so the guard cannot be used to probe the rest of the filesystem.
   */
  public static Resolution resolveFileWithinRoot(Path root, String requested) {
    if (root == null || requested == null || requested.isBlank() || requested.indexOf('\0') >= 0) {
      return new Resolution(Status.INVALID, null);
    }
    Path candidate;
    try {
      Path raw = Paths.get(requested.trim());
      candidate = (raw.isAbsolute() ? raw : root.resolve(raw)).toAbsolutePath().normalize();
      if (raw.isAbsolute() && !candidate.startsWith(root)) {
        candidate = rebaseOntoCanonicalRoot(root, candidate);
      }
    } catch (InvalidPathException e) {
      return new Resolution(Status.INVALID, null);
    }
    if (!candidate.startsWith(root)) {
      return new Resolution(Status.OUTSIDE_ROOT, null);
    }
    if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
      return new Resolution(Status.NOT_FOUND, null);
    }
    Path real;
    try {
      real = candidate.toRealPath();
    } catch (IOException e) {
      // Dangling symlink or unreadable entry
      return new Resolution(Status.NOT_FOUND, null);
    }
    if (!real.startsWith(root)) {
      return new Resolution(Status.OUTSIDE_ROOT, null);
    }
    if (!Files.isRegularFile(real)) {
      return new Resolution(Status.NOT_FOUND, null);
    }
    return new Resolution(Status.OK, real);
  }

  /**
   * If an ancestor directory of {@code candidate} is a symlinked alias of {@code root} (for example
   * macOS {@code /var/folders/...} vs canonical {@code /private/var/folders/...}), rewrites that
   * prefix to {@code root} without following symlinks inside {@code root}.
   */
  private static Path rebaseOntoCanonicalRoot(Path root, Path candidate) {
    for (Path ancestor = candidate.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
      try {
        if (Files.isDirectory(ancestor) && ancestor.toRealPath().equals(root)) {
          return root.resolve(ancestor.relativize(candidate)).normalize();
        }
      } catch (IOException ignored) {
        // Unresolvable ancestor; continue walking up
      }
    }
    return candidate;
  }

  /** True when {@code snapshotId} matches the strict generated snapshot identifier format. */
  public static boolean isValidSnapshotId(String snapshotId) {
    return snapshotId != null && SNAPSHOT_ID_PATTERN.matcher(snapshotId).matches();
  }

  /**
   * True when {@code relPath} is a safe, root-relative path (no absolute path, no {@code ..}
   * segments, no NUL bytes) whose normalized form stays inside {@code base}.
   */
  public static boolean isSafeRelativePath(Path base, String relPath) {
    if (base == null || relPath == null || relPath.isBlank() || relPath.indexOf('\0') >= 0) {
      return false;
    }
    try {
      Path rel = Paths.get(relPath);
      if (rel.isAbsolute()) {
        return false;
      }
      for (Path segment : rel) {
        if ("..".equals(segment.toString())) {
          return false;
        }
      }
      return base.resolve(rel).normalize().startsWith(base);
    } catch (InvalidPathException e) {
      return false;
    }
  }

  /**
   * Ensures {@code candidate} stays inside {@code canonicalRoot} and no segment from {@code
   * canonicalRoot} to {@code candidate} is a symbolic link.
   */
  public static void ensureNoSymlinkOnPath(Path canonicalRoot, Path candidate) throws IOException {
    Path normalized = candidate.toAbsolutePath().normalize();
    if (!normalized.startsWith(canonicalRoot)) {
      throw new IOException("Refusing filesystem access outside project root: " + candidate);
    }
    Path rel = canonicalRoot.relativize(normalized);
    Path cursor = canonicalRoot;
    for (Path part : rel) {
      cursor = cursor.resolve(part);
      if (Files.isSymbolicLink(cursor)) {
        throw new IOException("Refusing filesystem access across symlink: " + cursor);
      }
    }
  }
}
