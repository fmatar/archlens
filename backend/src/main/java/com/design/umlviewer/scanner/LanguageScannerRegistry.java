package com.design.umlviewer.scanner;

import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class LanguageScannerRegistry {

  @Inject Instance<LanguageScanner> availableScanners;

  private final List<LanguageScanner> manualScanners = new ArrayList<>();

  public LanguageScannerRegistry() {}

  public LanguageScannerRegistry(List<LanguageScanner> scanners) {
    if (scanners != null) {
      this.manualScanners.addAll(scanners);
    }
  }

  public void register(LanguageScanner scanner) {
    if (scanner != null) {
      manualScanners.add(scanner);
    }
  }

  public List<LanguageScanner> getAllScanners() {
    List<LanguageScanner> all = new ArrayList<>(manualScanners);
    if (availableScanners != null) {
      for (LanguageScanner s : availableScanners) {
        if (all.stream()
            .noneMatch(existing -> existing.languageId().equalsIgnoreCase(s.languageId()))) {
          all.add(s);
        }
      }
    }
    return all;
  }

  public Optional<LanguageScanner> findScanner(String languageId) {
    if (languageId == null || languageId.isBlank()) {
      return Optional.empty();
    }
    return getAllScanners().stream()
        .filter(s -> s.languageId().equalsIgnoreCase(languageId.trim()))
        .findFirst();
  }

  public LanguageScanner resolveScanner(String projectRoot, ArchitecturePolicy policy) {
    if (policy != null && policy.lang() != null && !policy.lang().isBlank()) {
      Optional<LanguageScanner> explicit = findScanner(policy.lang());
      if (explicit.isPresent()) {
        return explicit.get();
      }
    }

    for (LanguageScanner scanner : getAllScanners()) {
      if (scanner.supports(projectRoot, policy)) {
        return scanner;
      }
    }

    // Default fallback to Java scanner if available, or first available scanner
    return findScanner("java").orElseGet(() -> getAllScanners().stream().findFirst().orElse(null));
  }
}
