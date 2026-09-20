package com.design.umlviewer.domain.model;

import java.util.List;

public record MethodNode(
    String name,
    List<String> args,
    String returnType,
    boolean isPrivate,
    int cc,
    double coverage,
    double crap,
    int killed,
    int survived,
    int uncovered,
    int line) {}
