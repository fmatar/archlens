package io.slixes.archlens.delivery.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Boots the app with authentication enabled and a fixed token. */
@QuarkusTest
@TestProfile(SessionTokenFilterTest.AuthEnabledProfile.class)
class SessionTokenFilterTest {

  @Inject SessionTokenService sessionTokens;

  static final String TOKEN = "test-session-token-0123456789";

  public static class AuthEnabledProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("archlens.auth.enabled", "true", "archlens.auth.token", TOKEN);
    }
  }

  private static RestAssuredConfig noRedirects() {
    return RestAssuredConfig.config()
        .redirect(RedirectConfig.redirectConfig().followRedirects(false));
  }

  @Test
  void apiRequiresToken() {
    given().when().get("/api/fitness").then().statusCode(401).body("error", is("unauthorized"));
    given().header("X-Archlens-Token", "wrong").when().get("/api/fitness").then().statusCode(401);
    given()
        .header("Authorization", "Bearer wrong")
        .when()
        .get("/api/projects")
        .then()
        .statusCode(401);
  }

  @Test
  void apiAcceptsHeaderBearerAndCookie() {
    given().header("X-Archlens-Token", TOKEN).when().get("/api/fitness").then().statusCode(200);
    given()
        .header("Authorization", "Bearer " + TOKEN)
        .when()
        .get("/api/fitness")
        .then()
        .statusCode(200);
    given()
        .cookie(sessionTokens.cookieName(), TOKEN)
        .when()
        .get("/api/fitness")
        .then()
        .statusCode(200);
    given()
        .cookie(sessionTokens.cookieName(), "wrong")
        .when()
        .get("/api/fitness")
        .then()
        .statusCode(401);
  }

  @Test
  void mcpEndpointRequiresToken() {
    given().when().get("/mcp").then().statusCode(401);
    given().header("X-Archlens-Token", TOKEN).when().get("/mcp").then().statusCode(not(401));
  }

  @Test
  void versionAndHealthStayOpen() {
    given().when().get("/api/version").then().statusCode(200);
    given().when().get("/q/health").then().statusCode(200);
  }

  @Test
  void tokenQueryParamSetsHttpOnlyCookieAndRedirectsWithoutToken() {
    var response =
        given()
            .config(noRedirects())
            .when()
            .get("/?token=" + TOKEN + "&projectRoot=demo")
            .then()
            .statusCode(302)
            .header("Location", is("/?projectRoot=demo"))
            .extract();
    String setCookie = response.header("Set-Cookie");
    assertNotNull(setCookie);
    assertTrue(setCookie.contains("archlens_session_"));
    assertTrue(setCookie.toLowerCase().contains("httponly"), setCookie);
    assertTrue(setCookie.toLowerCase().contains("samesite=strict"));
    String cookieName = setCookie.substring(0, setCookie.indexOf('='));
    given().cookie(cookieName, TOKEN).when().get("/api/fitness").then().statusCode(200);
  }

  @Test
  void wrongQueryTokenDoesNotSetCookie() {
    given()
        .config(noRedirects())
        .when()
        .get("/?token=nope")
        .then()
        .statusCode(not(302))
        .header("Set-Cookie", nullValue());
  }

  @Test
  void stripTokenParamKeepsOtherParametersAndAvoidsOpenRedirects() {
    assertEquals("/", SessionTokenFilter.stripTokenParam("/", "token=a"));
    assertEquals("/?a=1&b=2", SessionTokenFilter.stripTokenParam("/", "a=1&token=x&b=2"));
    assertEquals("/", SessionTokenFilter.stripTokenParam("//evil.example", "token=x"));
    assertEquals("/", SessionTokenFilter.stripTokenParam(null, null));
  }

  @Test
  void protectedPathClassification() {
    assertTrue(SessionTokenFilter.isProtectedPath("/api/graph"));
    assertTrue(SessionTokenFilter.isProtectedPath("/api"));
    assertTrue(SessionTokenFilter.isProtectedPath("/mcp"));
    assertTrue(SessionTokenFilter.isProtectedPath("/mcp/sse"));
    assertTrue(SessionTokenFilter.isProtectedPath("/api;x/projects"));
    assertTrue(SessionTokenFilter.isProtectedPath("/api;/projects"));
    assertTrue(SessionTokenFilter.isProtectedPath("/api;jsessionid=1/projects"));
    assertTrue(SessionTokenFilter.isProtectedPath("/api%3Bx/projects"));
    assertTrue(SessionTokenFilter.isProtectedPath("/api%3bx/projects"));
    assertTrue(SessionTokenFilter.isProtectedPath("/API/projects"));
    assertTrue(SessionTokenFilter.isProtectedPath("/api;x/version"));
    assertTrue(SessionTokenFilter.isProtectedPath("/api//projects"));
    assertTrue(SessionTokenFilter.isProtectedPath("/mcp;x"));
    assertTrue(SessionTokenFilter.isProtectedPath("/mcp%3Bx"));
    assertFalse(SessionTokenFilter.isProtectedPath("/api/version"));
    assertFalse(SessionTokenFilter.isProtectedPath("/q/health"));
    assertFalse(SessionTokenFilter.isProtectedPath("/"));
    assertFalse(SessionTokenFilter.isProtectedPath("/assets/index.js"));
    assertFalse(SessionTokenFilter.isProtectedPath("/apix"));
    assertFalse(SessionTokenFilter.isProtectedPath(null));
    assertEquals("/api/projects", SessionTokenFilter.canonicalizePath("/api;x/projects"));
    assertEquals("/api/projects", SessionTokenFilter.canonicalizePath("/api%3Bx/projects"));
  }

  @Test
  void matrixParameterAndPathVariantsRequireToken() {
    for (String path :
        List.of(
            "/api;x/projects",
            "/api;/projects",
            "/api;jsessionid=1/projects",
            "/api;/graph",
            "/api;x/policy",
            "/api;x/snapshots",
            "/api;x/events",
            "/api;x/fs/directories?path=/etc",
            "/api;x/version",
            "/API/projects",
            "/Api/projects",
            "/api%3Bx/projects",
            "/api%3bx/projects",
            "/mcp;x",
            "/mcp;x/sse",
            "/mcp%3Bx")) {
      given()
          .config(noRedirects())
          .urlEncodingEnabled(false)
          .when()
          .get(path)
          .then()
          .statusCode(401);
    }

    for (String postPath :
        List.of(
            "/api;x/mailbox/to-agent",
            "/api;x/mailbox/verify",
            "/api;x/mailbox/rollback",
            "/api;x/mailbox/sync-adr",
            "/api;x/policy")) {
      given()
          .config(noRedirects())
          .urlEncodingEnabled(false)
          .contentType("application/json")
          .body("{}")
          .when()
          .post(postPath)
          .then()
          .statusCode(401);
    }
  }

  @Test
  void graphDoesNotAutoOpenProjectAndFsDeniesOutsideAllowedRoots(@TempDir Path tempDir)
      throws Exception {
    Path project = Files.createDirectories(tempDir.resolve("sample-proj"));
    Files.writeString(project.resolve("pom.xml"), "<project/>");
    String rootPath = project.toString();

    // M-6: arbitrary host directory listing outside allowed roots is forbidden
    given()
        .header("X-Archlens-Token", TOKEN)
        .queryParam("path", "/etc")
        .when()
        .get("/api/fs/directories")
        .then()
        .statusCode(403)
        .body("denied", is(true));

    // C-2: GET /api/graph succeeds with token, but must NOT mark the project as opened
    given()
        .header("X-Archlens-Token", TOKEN)
        .queryParam("projectRoot", rootPath)
        .when()
        .get("/api/graph")
        .then()
        .statusCode(200);

    given()
        .header("X-Archlens-Token", TOKEN)
        .queryParam("projectRoot", rootPath)
        .contentType("application/json")
        .body("{\"baselineViolations\":0,\"runTests\":true,\"confirmRunTests\":true}")
        .when()
        .post("/api/mailbox/verify")
        .then()
        .statusCode(403);

    // Explicit POST /api/projects/open authorizes the project for this session
    given()
        .header("X-Archlens-Token", TOKEN)
        .queryParam("projectRoot", rootPath)
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/projects/open")
        .then()
        .statusCode(200)
        .body("opened", is(true));

    // C-2: runTests=true without confirmRunTests=true is rejected with 400
    given()
        .header("X-Archlens-Token", TOKEN)
        .queryParam("projectRoot", rootPath)
        .contentType("application/json")
        .body("{\"baselineViolations\":0,\"runTests\":true}")
        .when()
        .post("/api/mailbox/verify")
        .then()
        .statusCode(400);

    // Non-test verification passes once explicitly opened
    given()
        .header("X-Archlens-Token", TOKEN)
        .queryParam("projectRoot", rootPath)
        .contentType("application/json")
        .body("{\"baselineViolations\":0,\"runTests\":false}")
        .when()
        .post("/api/mailbox/verify")
        .then()
        .statusCode(200);
  }
}
