package io.slixes.archlens.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Level 1 Application registry of project roots that have been opened in this workbench session (a
 * graph was compiled or an event stream was subscribed for them). Operations that execute code or
 * delete files are only allowed on opened projects.
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
