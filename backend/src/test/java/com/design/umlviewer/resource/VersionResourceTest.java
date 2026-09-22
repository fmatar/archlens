package com.design.umlviewer.resource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VersionResourceTest {

  @Test
  void testGetVersion() {
    VersionResource resource = new VersionResource();
    resource.init();

    Map<String, String> info = resource.getVersion();
    assertNotNull(info);
    assertTrue(info.containsKey("version"));
    assertTrue(info.containsKey("commitId"));
    assertTrue(info.containsKey("branch"));
    assertTrue(info.containsKey("buildTime"));
    assertFalse(info.get("version").isBlank());
    assertThrows(UnsupportedOperationException.class, () -> info.put("test", "test"));
  }
}
