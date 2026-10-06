package com.design.umlviewer.delivery.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FitnessResourceTest {

  @Test
  @DisplayName("GET /api/fitness should return composite fitness evaluation with grade and rules")
  void shouldReturnFitnessEvaluation() {
    given()
        .when()
        .get("/api/fitness")
        .then()
        .statusCode(200)
        .body("fitnessScore", notNullValue())
        .body("grade", notNullValue())
        .body("rules", hasSize(greaterThanOrEqualTo(4)))
        .body("summaryMetrics", notNullValue());
  }

  @Test
  @DisplayName("GET /api/fitness/history should return regression trend with historical snapshots")
  void shouldReturnFitnessHistory() {
    given()
        .when()
        .get("/api/fitness/history")
        .then()
        .statusCode(200)
        .body("trendDirection", notNullValue())
        .body("scoreDelta", notNullValue())
        .body("history", notNullValue());
  }
}
