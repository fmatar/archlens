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
import java.util.Map;
import org.junit.jupiter.api.Test;

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
    assertFalse(SessionTokenFilter.isProtectedPath("/api/version"));
    assertFalse(SessionTokenFilter.isProtectedPath("/q/health"));
    assertFalse(SessionTokenFilter.isProtectedPath("/"));
    assertFalse(SessionTokenFilter.isProtectedPath("/assets/index.js"));
    assertFalse(SessionTokenFilter.isProtectedPath("/apix"));
  }
}
