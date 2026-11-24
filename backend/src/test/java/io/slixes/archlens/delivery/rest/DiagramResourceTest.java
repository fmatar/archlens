package io.slixes.archlens.delivery.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slixes.archlens.domain.dossier.ArchitecturalDossierGenerator;
import io.slixes.archlens.domain.dossier.DipInversionPlan;
import io.slixes.archlens.domain.dossier.DipInversionSynthesizer;
import io.slixes.archlens.domain.dossier.MermaidDiagramGenerator;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.domain.screaming.ScreamingMigrationAssistant;
import io.slixes.archlens.engine.ArchitectureCompiler;
import io.slixes.archlens.engine.GraphCompiler;
import io.slixes.archlens.engine.ProjectFileWatcher;
import io.slixes.archlens.usecase.ExportDiagramUseCase;
import io.slixes.archlens.usecase.ExportDossierUseCase;
import io.slixes.archlens.usecase.ManageSnapshotsUseCase;
import io.slixes.archlens.usecase.OpenedProjectRegistry;
import io.slixes.archlens.usecase.ProposeScreamingMigrationUseCase;
import io.slixes.archlens.usecase.ResolveFilesystemUseCase;
import io.slixes.archlens.usecase.SavePolicyUseCase;
import io.slixes.archlens.usecase.SynthesizeDipInversionUseCase;
import io.slixes.archlens.workspace.fs.LocalFilesystemGateway;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagramResourceTest {

  private static DiagramResource buildDiagramResource(
      ArchitectureCompiler compiler,
      OpenedProjectRegistry openedProjects,
      ProjectFileWatcher watcher) {
    ObjectMapper mapper = new ObjectMapper();
    return new DiagramResource(
        compiler,
        new ExportDossierUseCase(compiler, new ArchitecturalDossierGenerator()),
        new ExportDiagramUseCase(compiler, new MermaidDiagramGenerator()),
        new SynthesizeDipInversionUseCase(compiler, new DipInversionSynthesizer()),
        new ProposeScreamingMigrationUseCase(compiler, new ScreamingMigrationAssistant()),
        new SavePolicyUseCase(compiler, mapper),
        openedProjects,
        watcher);
  }

  private static ProjectFilesystemResource buildFilesystemResource(
      OpenedProjectRegistry openedProjects, ProjectFileWatcher watcher) {
    return new ProjectFilesystemResource(
        new ResolveFilesystemUseCase(new LocalFilesystemGateway()), openedProjects, watcher);
  }

  @Test
  void testDiagramResourceEndpoints(@TempDir Path tempDir) throws Exception {
    GraphCompiler compiler =
        new GraphCompiler() {
          @Override
          public ArchitecturePolicy loadPolicy(String root) {
            return new ArchitecturePolicy(
                "Test", "src", "com", true, List.of(), List.of(), List.of(), List.of(), List.of());
          }

          @Override
          public ArchitectureGraph compileGraph(String root, String proposalId) {
            return new ArchitectureGraph(
                "Graph", false, proposalId, List.of(), List.of(), List.of());
          }
        };

    OpenedProjectRegistry openedProjects = new OpenedProjectRegistry();
    ProjectFileWatcher watcher = new ProjectFileWatcher();
    DiagramResource resource = buildDiagramResource(compiler, openedProjects, watcher);
    ProjectFilesystemResource fsResource = buildFilesystemResource(openedProjects, watcher);

    try {
      ArchitectureGraph graph = resource.getGraph(tempDir.toString(), "prop-1");
      assertEquals("Graph", graph.title());
      assertEquals("prop-1", graph.activeProposalId());

      ArchitectureGraph diagramAlias = resource.getDiagram(tempDir.toString(), "prop-1");
      assertEquals("Graph", diagramAlias.title());

      String dossier = resource.getLlmDossier(tempDir.toString(), "prop-1");
      assertNotNull(dossier);
      assertTrue(dossier.contains("# Clean Architecture Optimization Dossier — Graph"));
      assertTrue(dossier.contains("Actionable LLM Refactoring Instructions"));

      String dossierAlias = resource.getLlmDossierAlias(tempDir.toString(), "prop-1");
      assertEquals(dossier, dossierAlias);

      String ringsExport =
          resource.getDiagramExport(
              tempDir.toString(), "prop-1", "COMPONENT_RINGS", null, false, null, null);
      assertTrue(ringsExport.startsWith("flowchart TB"));

      String umlExport =
          resource.getDiagramExport(
              tempDir.toString(), "prop-1", "CLASS_UML", null, true, null, null);
      assertTrue(umlExport.startsWith("classDiagram"));

      String dipExport =
          resource.getGraphExportAlias(
              tempDir.toString(),
              "prop-1",
              "DIP_REFACTOR",
              null,
              false,
              "com.example.OrderService",
              "com.example.PostgresRepo");
      assertTrue(dipExport.contains("subgraph Before"));
      assertTrue(dipExport.contains("subgraph After"));

      DipInversionPlan plan =
          resource.getInvertPlan(
              "com.example.OrderService", "com.example.PostgresRepo", tempDir.toString(), "prop-1");
      assertEquals("com.example.OrderService", plan.fromClass());
      assertEquals("com.example.PostgresRepo", plan.toClass());

      assertThrows(
          BadRequestException.class,
          () -> resource.getInvertPlan("", "com.example.PostgresRepo", tempDir.toString(), null));
      assertThrows(
          BadRequestException.class,
          () -> resource.getInvertPlan("com.example.OrderService", null, tempDir.toString(), null));

      ArchitecturePolicy policy = resource.getPolicy(tempDir.toString());
      assertEquals("Test", policy.title());

      ArchitecturePolicy validPolicy =
          new ArchitecturePolicy(
              "Saved Policy",
              "src/main/java",
              "com.test",
              true,
              List.of("domain", "application"),
              List.of(List.of("domain"), List.of("application")),
              List.of(),
              List.of(),
              List.of(),
              "java");
      WebApplicationException unopenedSave =
          assertThrows(
              WebApplicationException.class,
              () -> resource.savePolicy(tempDir.toString(), validPolicy));
      assertEquals(403, unopenedSave.getResponse().getStatus());

      Map<String, Object> openRes = fsResource.openProject(tempDir.toString(), null);
      assertEquals(Boolean.TRUE, openRes.get("opened"));

      assertNotNull(resource.streamEvents());
      assertNotNull(resource.streamEvents(tempDir.toString()));
      resource.notifyGraphUpdate(Map.of("event", "graph-update"));

      assertNotNull(resource.getCycles(tempDir.toString(), null));
      assertNotNull(resource.getScreamingMetric(tempDir.toString(), null));
      assertNotNull(resource.getScreamingMigrationProposal(tempDir.toString(), null));

      ArchitectureGraph savedGraph = resource.savePolicy(tempDir.toString(), validPolicy);
      assertNotNull(savedGraph);
    } finally {
      watcher.stop();
    }
  }

  @Test
  void testContainerWorkspaceMultiProjectDiscovery(@TempDir Path tempDir) throws IOException {
    ProjectFilesystemResource fsResource =
        buildFilesystemResource(new OpenedProjectRegistry(), new ProjectFileWatcher());

    Path workspaceDir = tempDir.resolve("workspace");
    Files.createDirectories(workspaceDir);

    Path fluo = workspaceDir.resolve("fluo");
    Files.createDirectories(fluo);
    Files.writeString(fluo.resolve("pom.xml"), "<project></project>");

    Path datarobot = workspaceDir.resolve("datarobot");
    Files.createDirectories(datarobot);
    Files.writeString(datarobot.resolve("go.mod"), "module datarobot\n");

    Path labs = workspaceDir.resolve("labs");
    Path archlens = labs.resolve("archlens");
    Files.createDirectories(archlens);
    Files.writeString(archlens.resolve("pom.xml"), "<project></project>");

    Path backend = archlens.resolve("backend");
    Files.createDirectories(backend);
    Files.writeString(backend.resolve("pom.xml"), "<project></project>");

    Path frontend = archlens.resolve("frontend");
    Files.createDirectories(frontend);
    Files.writeString(frontend.resolve("package.json"), "{}");

    Path designSystem = labs.resolve("design-system");
    Files.createDirectories(designSystem);
    Files.writeString(designSystem.resolve("package.json"), "{}");

    Path ignored = workspaceDir.resolve("ignored_dir");
    Path target = ignored.resolve("target");
    Files.createDirectories(target);
    Files.writeString(target.resolve("pom.xml"), "<project></project>");

    Path hidden = workspaceDir.resolve(".hidden_repo");
    Files.createDirectories(hidden);
    Files.writeString(hidden.resolve("pom.xml"), "<project></project>");

    System.setProperty("archlens.container.workspace", workspaceDir.toString());
    try {
      Map<String, Object> result = fsResource.listProjects();
      @SuppressWarnings("unchecked")
      List<Map<String, String>> discovered = (List<Map<String, String>>) result.get("discovered");
      List<String> names = discovered.stream().map(m -> m.get("name")).toList();
      assertTrue(names.contains("fluo"));
      assertTrue(names.contains("datarobot"));
      assertTrue(names.contains("labs / archlens"));
      assertTrue(names.contains("labs / archlens / backend"));
      assertTrue(names.contains("labs / archlens / frontend"));
      assertTrue(names.contains("labs / design-system"));
      assertFalse(names.stream().anyMatch(n -> n.contains(".hidden")));
      assertFalse(names.stream().anyMatch(n -> n.contains("ignored_dir / target")));
    } finally {
      System.clearProperty("archlens.container.workspace");
    }
  }

  @Test
  void testSingleRepoMountedContainerWorkspace(@TempDir Path tempDir) throws IOException {
    ProjectFilesystemResource fsResource =
        buildFilesystemResource(new OpenedProjectRegistry(), new ProjectFileWatcher());

    Path repoDir = tempDir.resolve("my-app");
    Files.createDirectories(repoDir);
    Files.writeString(repoDir.resolve("pom.xml"), "<project></project>");

    Path coreModule = repoDir.resolve("core");
    Files.createDirectories(coreModule);
    Files.writeString(coreModule.resolve("pom.xml"), "<project></project>");

    System.setProperty("archlens.container.workspace", repoDir.toString());
    try {
      Map<String, Object> result = fsResource.listProjects();
      @SuppressWarnings("unchecked")
      List<Map<String, String>> discovered = (List<Map<String, String>>) result.get("discovered");
      List<String> names = discovered.stream().map(m -> m.get("name")).toList();
      assertTrue(names.contains("my-app"));
      assertTrue(names.contains("my-app / core"));
    } finally {
      System.clearProperty("archlens.container.workspace");
    }
  }

  @Test
  void testSnapshotsEndpointsWithGitTags(@TempDir Path tempDir) throws Exception {
    GraphCompiler compiler =
        new GraphCompiler() {
          @Override
          public ArchitectureGraph compileGraph(String root, String proposalId) {
            return new ArchitectureGraph(
                "Graph", false, proposalId, List.of(), List.of(), List.of());
          }
        };

    SnapshotResource snapshotResource =
        new SnapshotResource(new ManageSnapshotsUseCase(compiler, new ObjectMapper()));

    Path gitRepoDir = tempDir.resolve("gitrepo");
    Files.createDirectories(gitRepoDir);
    new ProcessBuilder("git", "init").directory(gitRepoDir.toFile()).start().waitFor();
    new ProcessBuilder("git", "config", "user.email", "ci@archlens.io")
        .directory(gitRepoDir.toFile())
        .start()
        .waitFor();
    new ProcessBuilder("git", "config", "user.name", "Archlens CI")
        .directory(gitRepoDir.toFile())
        .start()
        .waitFor();
    new ProcessBuilder("git", "commit", "--allow-empty", "-m", "Initial commit")
        .directory(gitRepoDir.toFile())
        .start()
        .waitFor();
    new ProcessBuilder("git", "tag", "v1.0.0").directory(gitRepoDir.toFile()).start().waitFor();

    Map<String, Object> gitRepoSnapshots = snapshotResource.listSnapshots(gitRepoDir.toString());
    @SuppressWarnings("unchecked")
    List<Map<String, String>> snapshotList =
        (List<Map<String, String>>) gitRepoSnapshots.get("snapshots");
    assertFalse(snapshotList.isEmpty());
    assertEquals("v1.0.0", snapshotList.get(0).get("tag"));
  }

  @Test
  void testBindsToRing1WorkspacePathResolverUseCase() throws IOException {
    Path sourceFile =
        Path.of("src/main/java/io/slixes/archlens/delivery/rest/DiagramResource.java");
    String source = Files.readString(sourceFile);
    assertTrue(
        source.contains("import io.slixes.archlens.usecase.WorkspacePathResolver;"),
        "DiagramResource must explicitly import Ring 1 io.slixes.archlens.usecase.WorkspacePathResolver");
  }

  @Test
  void testEnsureWatcherRunningRejectsUnopenedRootAndSwitchesOnOpenProject(@TempDir Path tempDir)
      throws Exception {
    Path unopened = Files.createDirectories(tempDir.resolve("unopened-watch"));
    Path second = Files.createDirectories(tempDir.resolve("opened-watch-b"));
    OpenedProjectRegistry openedProjects = new OpenedProjectRegistry();
    ProjectFileWatcher watcher = new ProjectFileWatcher();
    DiagramResource resource = buildDiagramResource(new GraphCompiler(), openedProjects, watcher);
    ProjectFilesystemResource fsResource = buildFilesystemResource(openedProjects, watcher);
    try {
      resource.ensureWatcherRunning(unopened.toString());
      Thread.sleep(150);
      assertFalse(
          watcher.isRunning(),
          "ensureWatcherRunning must not watch an unopened external directory");

      fsResource.openProject(unopened.toString(), null);
      resource.ensureWatcherRunning(unopened.toString());
      for (int i = 0; i < 20 && watcher.getWatchedRoot() == null; i++) {
        Thread.sleep(50);
      }
      assertEquals(unopened.toRealPath(), watcher.getWatchedRoot());

      fsResource.openProject(second.toString(), null);
      assertEquals(second.toRealPath(), watcher.getWatchedRoot());
    } finally {
      watcher.stop();
    }
  }
}
