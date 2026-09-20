package com.design.umlviewer.metrics;

import com.design.umlviewer.domain.model.CrapScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrapScoreCalculatorTest {

    private final CrapScoreCalculator calculator = new CrapScoreCalculator();

    @Test
    void testCalculateMethodCrap() {
        // 100% coverage -> CRAP equals CC
        double crapFullCov = calculator.calculateMethodCrap(5, 1.0);
        assertEquals(5.0, crapFullCov);

        // 0% coverage -> CRAP = CC^2 * 1^3 + CC = CC^2 + CC
        double crapZeroCov = calculator.calculateMethodCrap(4, 0.0);
        assertEquals(20.0, crapZeroCov); // 16 + 4

        // Negative coverage bound check (clamped to 0)
        double crapNegative = calculator.calculateMethodCrap(2, -0.5);
        assertEquals(6.0, crapNegative); // 4 + 2

        // Greater than 1 coverage bound check (clamped to 1)
        double crapOver = calculator.calculateMethodCrap(2, 1.5);
        assertEquals(2.0, crapOver);
    }

    @Test
    void testAggregate() {
        // Empty or null list
        assertEquals(0.0, calculator.aggregate(null).mu());
        assertEquals(0.0, calculator.aggregate(List.of()).mu());

        // Non-empty list
        List<Double> scores = List.of(2.0, 4.0, 6.0);
        CrapScore aggregated = calculator.aggregate(scores);

        assertEquals(4.0, aggregated.mu());
        assertEquals(6.0, aggregated.max());
        assertTrue(aggregated.sigma() > 0);
    }
}
