package io.slixes.archlens.domain.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CrapScoreCalculatorTest {

  private final CrapScoreCalculator calculator = new CrapScoreCalculator();

  @Test
  void testCalculateMethodCrap() {
    assertEquals(5.0, calculator.calculateMethodCrap(5, 1.0));
    assertEquals(20.0, calculator.calculateMethodCrap(4, 0.0));
    assertEquals(6.0, calculator.calculateMethodCrap(2, -0.5));
    assertEquals(2.0, calculator.calculateMethodCrap(2, 1.5));
  }

  @Test
  void testAggregate() {
    assertEquals(0.0, calculator.aggregate(null).mu());
    assertEquals(0.0, calculator.aggregate(List.of()).mu());

    List<Double> scores = List.of(2.0, 4.0, 6.0);
    CrapScore aggregated = calculator.aggregate(scores);

    assertEquals(4.0, aggregated.mu());
    assertEquals(6.0, aggregated.max());
    assertTrue(aggregated.sigma() > 0);
  }
}
