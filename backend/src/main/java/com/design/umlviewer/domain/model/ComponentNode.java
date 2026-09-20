package com.design.umlviewer.domain.model;

import java.util.List;

public record ComponentNode(
    String id,
    String label,
    Integer level,
    CrapScore crap,
    Double mutationScore,
    List<String> childPackageIds,
    List<ClassNode> classes
) {}
