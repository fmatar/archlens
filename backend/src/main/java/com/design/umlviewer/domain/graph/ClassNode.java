package com.design.umlviewer.domain.graph;

import java.util.List;

public record ClassNode(
    String id,
    String name,
    String packageName,
    String filePath,
    Stereotype stereotype,
    boolean isForeign,
    Integer level,
    CrapScore crap,
    Double coverage,
    Integer cc,
    Integer killed,
    Integer survived,
    Integer uncovered,
    List<FieldNode> fields,
    List<MethodNode> methods) {
  public enum Stereotype {
    CLASS,
    INTERFACE,
    RECORD,
    ENUM,
    ABSTRACT
  }

  public ClassNode withId(String newId) {
    return new ClassNode(
        newId,
        name,
        packageName,
        filePath,
        stereotype,
        isForeign,
        level,
        crap,
        coverage,
        cc,
        killed,
        survived,
        uncovered,
        fields,
        methods);
  }
}
