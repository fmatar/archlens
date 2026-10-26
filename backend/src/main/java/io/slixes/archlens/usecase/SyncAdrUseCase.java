package io.slixes.archlens.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Use case that automatically generates and synchronizes Architecture Decision Records (ADRs) in
 * docs/adr/ when DIP interface inversions or package reorganizations are performed.
 */
@ApplicationScoped
public class SyncAdrUseCase {

  private static final String ADR_DIR = "docs/adr";

  public record AdrDocument(
      int index,
      String filename,
      String title,
      String status,
      String date,
      String context,
      String decision,
      String consequences) {}

  public SyncAdrUseCase() {}

  /**
   * Synchronizes an ADR for an automated Dependency Inversion Principle (DIP) interface
   * introduction.
   */
  public AdrDocument recordDipInversion(
      String projectRoot,
      String sourceClass,
      String targetClass,
      String interfacePortName,
      String reason)
      throws IOException {
    String title =
        "Invert outward dependency from "
            + getSimpleName(sourceClass)
            + " using "
            + interfacePortName;
    String context =
        "The class `"
            + sourceClass
            + "` had an illegal outward Clean Architecture dependency coupling to `"
            + targetClass
            + "`. "
            + (reason != null && !reason.isBlank()
                ? reason
                : "Direct outer layer imports violate Uncle Bob's Dependency Rule.");
    String decision =
        "Introduce the interface port `"
            + interfacePortName
            + "` inside the inner domain/application layer and invert the dependency so that outer adapters implement this port.";
    String consequences =
        "Inner layers remain independent of outer framework/database details. Increases testability and decoupling, requires maintaining the interface contract.";

    return createAdr(projectRoot, title, context, decision, consequences);
  }

  /**
   * Synchronizes an ADR for a package screaming architecture reorganization or layer restructuring.
   */
  public AdrDocument recordPackageReorganization(
      String projectRoot, String title, String context, String decision, String consequences)
      throws IOException {
    return createAdr(projectRoot, title, context, decision, consequences);
  }

  private AdrDocument createAdr(
      String projectRoot, String title, String context, String decision, String consequences)
      throws IOException {
    File root = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    File adrDirectory = new File(root, ADR_DIR);
    if (!adrDirectory.exists()) {
      Files.createDirectories(adrDirectory.toPath());
    }

    int nextIndex = determineNextAdrIndex(adrDirectory);
    String slug =
        title
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    if (slug.length() > 60) {
      slug = slug.substring(0, 60);
    }
    String filename = String.format("%04d-%s.md", nextIndex, slug);
    String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

    String markdown =
        "# "
            + nextIndex
            + ". "
            + title
            + "\n\n"
            + "Date: "
            + date
            + "\n\n"
            + "## Status\n\n"
            + "Accepted\n\n"
            + "## Context\n\n"
            + context
            + "\n\n"
            + "## Decision\n\n"
            + decision
            + "\n\n"
            + "## Consequences\n\n"
            + consequences
            + "\n";

    File targetFile = new File(adrDirectory, filename);
    Files.writeString(targetFile.toPath(), markdown);

    return new AdrDocument(
        nextIndex, filename, title, "Accepted", date, context, decision, consequences);
  }

  private int determineNextAdrIndex(File adrDirectory) {
    File[] existing = adrDirectory.listFiles((dir, name) -> name.matches("^\\d{4}-.*\\.md$"));
    if (existing == null || existing.length == 0) {
      return 1;
    }
    return Arrays.stream(existing)
            .map(File::getName)
            .map(name -> name.substring(0, 4))
            .mapToInt(
                s -> {
                  try {
                    return Integer.parseInt(s);
                  } catch (NumberFormatException e) {
                    return 0;
                  }
                })
            .max()
            .orElse(0)
        + 1;
  }

  private String getSimpleName(String fqcn) {
    if (fqcn == null) return "";
    int lastDot = fqcn.lastIndexOf('.');
    return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
  }
}
