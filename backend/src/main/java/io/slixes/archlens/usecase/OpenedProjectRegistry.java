package io.slixes.archlens.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Level 1 Application registry of project roots that have been explicitly opened in this workbench
 * session (via {@code POST /api/projects/open}). Operations that execute code, mutate policies,
 * append agent commands, or delete files are only allowed on opened projects.
 */
@ApplicationScoped
public class OpenedProjectRegistry {

  private final Set<Path> openedRoots = ConcurrentHashMap.newKeySet();

  /** Registers the canonical form of {@code projectRoot}; returns it, or empty if unresolvable. */
  public Optional<Path> markOpened(String projectRoot) {
    Optional<Path> root = ProjectPathGuard.tryCanonicalRoot(projectRoot);
    root.ifPresent(openedRoots::add);
    return root;
  }

  /** True when the canonical form of {@code projectRoot} was opened in this session. */
  public boolean isOpened(String projectRoot) {
    return ProjectPathGuard.tryCanonicalRoot(projectRoot).map(openedRoots::contains).orElse(false);
  }
}
