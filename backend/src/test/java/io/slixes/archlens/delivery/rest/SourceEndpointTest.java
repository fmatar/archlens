package io.slixes.archlens.delivery.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SourceEndpointTest {

  @Test
  void sourceOutsideProjectRootIsForbidden() throws Exception {
    Path base = Files.createTempDirectory("archlens-src-test");
    Path project = Files.createDirectories(base.resolve("project"));
    Path secret = Files.writeString(base.resolve("secret.txt"), "TOP-SECRET");
    Files.writeString(project.resolve("A.java"), "class A {}");

    given()
        .queryParam("filePath", secret.toString())
        .queryParam("projectRoot", project.toString())
        .when()
        .get("/api/source")
        .then()
        .statusCode(403)
        .body("denied", is(true))
        .body(not(containsString("TOP-SECRET")));

    given()
        .queryParam("filePath", "../secret.txt")
        .queryParam("projectRoot", project.toString())
        .when()
        .get("/api/source")
        .then()
        .statusCode(403);

    given()
        .queryParam("filePath", "A.java")
        .queryParam("projectRoot", project.toString())
        .when()
        .get("/api/source")
        .then()
        .statusCode(200)
        .body("content", is("class A {}"));
  }
}
