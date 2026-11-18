package io.slixes.archlens.delivery.rest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SessionTokenServiceTest {

  @Test
  void usesConfiguredTokenAndComparesExactly() {
    SessionTokenService service = new SessionTokenService(true, "abc123", "127.0.0.1", 8088);
    assertTrue(service.isEnabled());
    assertEquals("abc123", service.getToken());
    assertTrue(service.matches("abc123"));
    assertFalse(service.matches("abc12"));
    assertFalse(service.matches("abc1234"));
    assertFalse(service.matches(""));
    assertFalse(service.matches(null));
    assertEquals("archlens_session_8088", service.cookieName());
  }

  @Test
  void generatesStrongRandomTokenWhenNotConfigured() {
    SessionTokenService a = new SessionTokenService(true, null, "127.0.0.1", 8088);
    SessionTokenService b = new SessionTokenService(true, "  ", "127.0.0.1", 8088);
    assertTrue(a.getToken().length() >= 43, "32 random bytes, base64url");
    assertEquals(a.getToken(), a.getToken(), "token is stable for the process lifetime");
    assertNotEquals(a.getToken(), b.getToken());
    assertTrue(a.getToken().matches("[A-Za-z0-9_-]+"));
  }

  @Test
  void classifiesLoopbackHosts() {
    assertTrue(SessionTokenService.isLoopbackHost("127.0.0.1"));
    assertTrue(SessionTokenService.isLoopbackHost("localhost"));
    assertTrue(SessionTokenService.isLoopbackHost("::1"));
    assertTrue(SessionTokenService.isLoopbackHost("[::1]"));
    assertFalse(SessionTokenService.isLoopbackHost("0.0.0.0"));
    assertFalse(SessionTokenService.isLoopbackHost("::"));
    assertFalse(SessionTokenService.isLoopbackHost("192.168.1.20"));
    assertFalse(SessionTokenService.isLoopbackHost(""));
    assertFalse(SessionTokenService.isLoopbackHost(null));
  }
}
