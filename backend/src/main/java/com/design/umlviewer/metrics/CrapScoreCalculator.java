package com.design.umlviewer.metrics;

import com.design.umlviewer.domain.model.CrapScore;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CrapScoreCalculator {

    /**
     * CRAP(m) = CC(m)^2 * (1 - Cov(m))^3 + CC(m)
     * where Cov is a fraction between 0.0 and 1.0.
     */
    public double calculateMethodCrap(int cyclomaticComplexity, double coverage) {
        double boundedCoverage = Math.max(0.0, Math.min(1.0, coverage));
        double covDiff = 1.0 - boundedCoverage;
        return (Math.pow(cyclomaticComplexity, 2) * Math.pow(covDiff, 3)) + cyclomaticComplexity;
    }

    public CrapScore aggregate(java.util.List<Double> craps) {
        if (craps == null || craps.isEmpty()) {
            return CrapScore.zero();
        }
        double sum = 0.0;
        double max = 0.0;
        for (double c : craps) {
            sum += c;
            if (c > max) max = c;
        }
        double mu = sum / craps.size();
        double varianceSum = 0.0;
        for (double c : craps) {
            varianceSum += Math.pow(c - mu, 2);
        }
        double sigma = Math.sqrt(varianceSum / craps.size());
        return new CrapScore(round(mu), round(max), round(sigma));
    }

    private double round(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
