package com.design.umlviewer.domain.model;

public record CrapScore(double mu, double max, double sigma) {
  public static CrapScore zero() {
    return new CrapScore(0.0, 0.0, 0.0);
  }
}
