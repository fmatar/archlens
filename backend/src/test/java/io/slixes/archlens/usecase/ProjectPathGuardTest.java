package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectPathGuardTest {

  @Test
  void canonicalRootResolvesExistingDirectoriesOnly(@TempDir Path tempDir) throws IOException {
    Path root = ProjectPathGuard.canonicalRoot(tempDir.toString());
    assertEquals(tempDir.toRealPath(), root);
    assertThrows(
        IOException.class,
        () -> ProjectPathGuard.canonicalRoot(tempDir.resolve("nope").toString()));
    Path file = Files.writeString(tempDir.resolve("f.txt"), "x");
    assertThrows(IOException.class, () -> ProjectPathGuard.canonicalRoot(file.toString()));
    assertTrue(ProjectPathGuard.tryCanonicalRoot(tempDir.resolve("nope").toString()).isEmpty());
  }

  @Test
  void resolvesRelativeAndAbsoluteFilesInsideRoot(@TempDir Path tempDir) throws IOException {
    Path root = tempDir.toRealPath();
    Path file = Files.createDirectories(root.resolve("src/a"));
    Path java = Files.writeString(file.resolve("A.java"), "class A {}");

    ProjectPathGuard.Resolution rel = ProjectPathGuard.resolveFileWithinRoot(root, "src/a/A.java");
    assertTrue(rel.isOk());
    assertEquals(java, rel.path());

    ProjectPathGuard.Resolution abs = ProjectPathGuard.resolveFileWithinRoot(root, java.toString());
    assertTrue(abs.isOk());

    ProjectPathGuard.Resolution dotted =
        ProjectPathGuard.resolveFileWithinRoot(root, "src/a/../a/A.java");
    assertTrue(dotted.isOk());
  }

  @Test
  void rejectsTraversalAndAbsolutePathsOutsideRoot(@TempDir Path tempDir) throws IOException {
    Path root = Files.createDirectories(tempDir.resolve("project")).toRealPath();
    Path secret = Files.writeString(tempDir.resolve("secret.txt"), "secret");

    assertEquals(
        ProjectPathGuard.Status.OUTSIDE_ROOT,
        ProjectPathGuard.resolveFileWithinRoot(root, "../secret.txt").status());
    assertEquals(
        ProjectPathGuard.Status.OUTSIDE_ROOT,
        ProjectPathGuard.resolveFileWithinRoot(root, secret.toString()).status());
    assertEquals(
        ProjectPathGuard.Status.OUTSIDE_ROOT,
        ProjectPathGuard.resolveFileWithinRoot(root, "../does-not-exist.txt").status(),
        "existence outside the root must not be revealed");
    assertEquals(
        ProjectPathGuard.Status.INVALID,
        ProjectPathGuard.resolveFileWithinRoot(root, "a\0b").status());
    assertEquals(
        ProjectPathGuard.Status.INVALID,
        ProjectPathGuard.resolveFileWithinRoot(root, " ").status());
  }

  @Test
  void rejectsSymlinksEscapingRoot(@TempDir Path tempDir) throws IOException {
    Path root = Files.createDirectories(tempDir.resolve("project")).toRealPath();
    Path secret = Files.writeString(tempDir.resolve("secret.txt"), "secret");
    Path outsideDir = Files.createDirectories(tempDir.resolve("outside"));
    Files.writeString(outsideDir.resolve("other.txt"), "other");
    Path inside = Files.writeString(root.resolve("inside.txt"), "inside");
    try {
      Files.createSymbolicLink(root.resolve("link-to-secret"), secret);
      Files.createSymbolicLink(root.resolve("link-dir"), outsideDir);
      Files.createSymbolicLink(root.resolve("link-inside"), inside);
    } catch (UnsupportedOperationException | IOException e) {
      return; // Symlinks not supported on this filesystem
    }

    assertEquals(
        ProjectPathGuard.Status.OUTSIDE_ROOT,
        ProjectPathGuard.resolveFileWithinRoot(root, "link-to-secret").status());
    assertEquals(
        ProjectPathGuard.Status.OUTSIDE_ROOT,
        ProjectPathGuard.resolveFileWithinRoot(root, "link-dir/other.txt").status());
    assertTrue(ProjectPathGuard.resolveFileWithinRoot(root, "link-inside").isOk());
  }

  @Test
  void missingAndDirectoryTargetsAreNotFound(@TempDir Path tempDir) throws IOException {
    Path root = tempDir.toRealPath();
    Files.createDirectories(root.resolve("dir"));
    assertEquals(
        ProjectPathGuard.Status.NOT_FOUND,
        ProjectPathGuard.resolveFileWithinRoot(root, "missing.java").status());
    assertEquals(
        ProjectPathGuard.Status.NOT_FOUND,
        ProjectPathGuard.resolveFileWithinRoot(root, "dir").status());
  }

  @Test
  void validatesSnapshotIdFormat() {
    assertTrue(ProjectPathGuard.isValidSnapshotId("snap-1731900000000-0a1b2c3d"));
    for (String bad :
        new String[] {
          null,
          "",
          "snap-test",
          "../snap-1-0a1b2c3d",
          "snap-1-0a1b2c3d/..",
          "snap-1-0A1B2C3D",
          "snap-1-0a1b2c3",
          "snap-1-0a1b2c3d\n",
          "snap--0a1b2c3d"
        }) {
      assertFalse(ProjectPathGuard.isValidSnapshotId(bad), "should reject: " + bad);
    }
  }

  @Test
  void validatesSafeRelativePaths(@TempDir Path tempDir) {
    Path base = tempDir.toAbsolutePath();
    assertTrue(ProjectPathGuard.isSafeRelativePath(base, "src/main/A.java"));
    assertFalse(ProjectPathGuard.isSafeRelativePath(base, "../A.java"));
    assertFalse(ProjectPathGuard.isSafeRelativePath(base, "src/../../A.java"));
    assertFalse(ProjectPathGuard.isSafeRelativePath(base, "/etc/passwd"));
    assertFalse(ProjectPathGuard.isSafeRelativePath(base, ""));
    assertFalse(ProjectPathGuard.isSafeRelativePath(null, "a"));
  }

  @Test
  void openedProjectRegistryTracksCanonicalRoots(@TempDir Path tempDir) throws IOException {
    Path project = Files.createDirectories(tempDir.resolve("p"));
    Path other = Files.createDirectories(tempDir.resolve("q"));
    OpenedProjectRegistry registry = new OpenedProjectRegistry();
    assertFalse(registry.isOpened(project.toString()));
    assertTrue(registry.markOpened(project.toString()).isPresent());
    assertTrue(registry.isOpened(project.toString()));
    assertTrue(registry.isOpened(other.resolve("../p").toString()));
    assertFalse(registry.isOpened(other.toString()));
    assertTrue(registry.markOpened(tempDir.resolve("missing").toString()).isEmpty());
    assertFalse(registry.isOpened(tempDir.resolve("missing").toString()));
  }
}
