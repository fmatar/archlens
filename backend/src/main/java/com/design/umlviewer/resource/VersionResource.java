package com.design.umlviewer.resource;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Path("/api/version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource {

  private final Map<String, String> versionInfo = new HashMap<>();

  @PostConstruct
  void init() {
    Properties props = new Properties();
    try (InputStream is =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("git.properties")) {
      if (is != null) {
        props.load(is);
      }
    } catch (IOException ignored) {
      // Fallback if git.properties cannot be read
    }

    versionInfo.put("version", props.getProperty("git.build.version", "0.0.1-Alpha-03"));
    versionInfo.put("commitId", props.getProperty("git.commit.id.abbrev", "unknown"));
    versionInfo.put("commitIdFull", props.getProperty("git.commit.id", "unknown"));
    versionInfo.put("branch", props.getProperty("git.branch", "unknown"));
    versionInfo.put("buildTime", props.getProperty("git.build.time", "unknown"));
    versionInfo.put("dirty", props.getProperty("git.dirty", "false"));
  }

  @GET
  public Map<String, String> getVersion() {
    return Collections.unmodifiableMap(versionInfo);
  }
}
