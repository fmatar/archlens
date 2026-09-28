package com.design.umlviewer.domain.dossier;

import java.util.List;

/**
 * Domain model representing an actionable Dependency Inversion Principle (DIP) remediation plan for
 * an illicit outward dependency violation in Clean Architecture.
 */
public record DipInversionPlan(
    String fromClass,
    String toClass,
    Integer fromLevel,
    Integer toLevel,
    String portName,
    String portPackage,
    String portFilePath,
    String portInterfaceCode,
    String adapterRefactorPreview,
    String callerRefactorPreview,
    String surgicalPrompt,
    List<String> targetMethods) {}
