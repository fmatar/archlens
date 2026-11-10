package io.slixes.archlens.domain.dossier;

import java.util.Locale;

/** Supported architectural diagram export views. */
public enum DiagramExportType {
  COMPONENT_RINGS,
  CLASS_UML,
  DIP_REFACTOR;

  public static DiagramExportType fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return COMPONENT_RINGS;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    if (normalized.contains("CLASS") || normalized.contains("UML")) {
      return CLASS_UML;
    }
    if (normalized.contains("DIP")
        || normalized.contains("REFACTOR")
        || normalized.contains("SANDBOX")) {
      return DIP_REFACTOR;
    }
    return COMPONENT_RINGS;
  }
}
