package io.slixes.archlens.delivery.rest;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.slixes.archlens.usecase.WorkspacePathResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Owns the per-launch session token that guards the workbench HTTP API ({@code /api/*}, {@code
 * /mcp}). The token is taken from {@code ARCHLENS_TOKEN} when provided (the CLI does this so it can
 * open the browser with it), otherwise generated randomly at startup. It is announced once in the
 * log as a ready-to-open URL and written to {@code ~/.archlens/session-<port>.json} (owner-only
 * permissions) so local tools such as the CLI MCP bridge can authenticate.
 */
// Loopback/wildcard literals are the security defaults this class enforces and reports on.
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
@ApplicationScoped
public class SessionTokenService {

  private static final Logger LOG = Logger.getLogger(SessionTokenService.class);
  private static final int TOKEN_BYTES = 32;

  @ConfigProperty(name = "archlens.auth.enabled", defaultValue = "true")
  boolean enabled;

  @ConfigProperty(name = "archlens.auth.token")
  Optional<String> configuredToken;

  @ConfigProperty(name = "quarkus.http.host", defaultValue = "127.0.0.1")
  String httpHost;

  @ConfigProperty(name = "quarkus.http.port", defaultValue = "8088")
  int httpPort;

  private static final SecureRandom RANDOM = new SecureRandom();

  private volatile String token;
  private Path sessionFile;
  private Path sessionDirOverride;

  public SessionTokenService() {}

  /** Test/utility constructor with explicit settings. */
  public SessionTokenService(boolean enabled, String token, String httpHost, int httpPort) {
    this(enabled, token, httpHost, httpPort, null);
  }

  SessionTokenService(
      boolean enabled, String token, String httpHost, int httpPort, Path sessionDirOverride) {
    this.enabled = enabled;
    this.configuredToken = Optional.ofNullable(token);
    this.httpHost = httpHost;
    this.httpPort = httpPort;
    this.sessionDirOverride = sessionDirOverride;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** The active session token, generated on first use when none was configured. */
  public String getToken() {
    String current = token;
    if (current == null) {
      synchronized (this) {
        if (token == null) {
          token =
              configuredToken
                  .map(String::trim)
                  .filter(t -> !t.isEmpty())
                  .orElseGet(SessionTokenService::generateToken);
        }
        current = token;
      }
    }
    return current;
  }

  /** Constant-time comparison of a presented credential with the session token. */
  public boolean matches(String presented) {
    if (presented == null || presented.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        presented.getBytes(StandardCharsets.UTF_8), getToken().getBytes(StandardCharsets.UTF_8));
  }

  /** Non-secret 12-character SHA-256 hex fingerprint of the active session token. */
  public String tokenFingerprint() {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(getToken().getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(12);
      for (int i = 0; i < 6; i++) {
        sb.append(String.format("%02x", digest[i]));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** Cookie name is port-scoped because browsers share cookies across ports of one host. */
  public String cookieName() {
    return "archlens_session_" + httpPort;
  }

  static String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** True when {@code host} is a loopback address or name. */
  static boolean isLoopbackHost(String host) {
    if (host == null || host.isBlank()) {
      return false;
    }
    String h = host.trim();
    if ("localhost".equalsIgnoreCase(h)) {
      return true;
    }
    if ("0.0.0.0".equals(h) || "::".equals(h) || "[::]".equals(h)) {
      return false;
    }
    try {
      return InetAddress.getByName(h.replace("[", "").replace("]", "")).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  void onStart(@Observes StartupEvent event) {
    if (LaunchMode.current() == LaunchMode.TEST) {
      return;
    }
    boolean interactiveTty = System.console() != null;
    boolean logTokenExplicit = "true".equalsIgnoreCase(System.getenv("ARCHLENS_LOG_TOKEN"));
    startLifecycle(interactiveTty, logTokenExplicit);
  }

  void startLifecycle(boolean interactiveTty, boolean logTokenExplicit) {
    warnIfExposed();
    if (!enabled) {
      LOG.warn(
          "Archlens API authentication is DISABLED (archlens.auth.enabled=false). Any local"
              + " process that can reach the port can use the workbench API.");
      return;
    }
    if (httpPort <= 0) {
      // MCP STDIO profile: HTTP is not meant to be used by clients
      return;
    }
    String displayHost = isLoopbackHost(httpHost) ? httpHost : "127.0.0.1";
    if (displayHost.contains(":") && !displayHost.startsWith("[")) {
      displayHost = "[" + displayHost + "]";
    }
    writeSessionFile();
    boolean hasConfiguredToken =
        configuredToken != null
            && configuredToken.map(String::trim).filter(t -> !t.isEmpty()).isPresent();
    if (hasConfiguredToken) {
      LOG.infof(
          "Archlens workbench ready on http://%s:%d/ (authenticated via ARCHLENS_TOKEN).",
          displayHost, httpPort);
    } else if (interactiveTty || logTokenExplicit) {
      LOG.infof(
          "Archlens workbench ready. Open: http://%s:%d/?token=%s",
          displayHost, httpPort, getToken());
    } else {
      Path sf =
          sessionFile != null
              ? sessionFile
              : resolveSessionDir().resolve("session-" + httpPort + ".json");
      LOG.infof(
          "Archlens workbench ready on http://%s:%d/. Read session token from %s (or set"
              + " ARCHLENS_LOG_TOKEN=true).",
          displayHost, httpPort, sf);
    }
  }

  void onStop(@Observes ShutdownEvent event) {
    Path file = sessionFile;
    if (file == null) {
      return;
    }
    try {
      if (Files.exists(file) && Files.readString(file).contains("\"" + getToken() + "\"")) {
        Files.delete(file);
      }
    } catch (IOException e) {
      LOG.debugf("Could not remove session file %s: %s", file, e.getMessage());
    }
  }

  private void warnIfExposed() {
    if (isLoopbackHost(httpHost)) {
      return;
    }
    if (WorkspacePathResolver.isContainerEnvironment()) {
      LOG.infof(
          "Archlens is listening on %s inside a container. Publish the port on loopback only"
              + " (docker run -p 127.0.0.1:8088:8088 ...).",
          httpHost);
    } else {
      LOG.warnf(
          "Archlens is listening on %s, which is reachable from other machines on your network."
              + " Unset ARCHLENS_HOST (default 127.0.0.1) unless remote access is intended.",
          httpHost);
    }
  }

  private Path resolveSessionDir() {
    return sessionDirOverride != null
        ? sessionDirOverride
        : Paths.get(System.getProperty("user.home"), ".archlens");
  }

  private void writeSessionFile() {
    try {
      Path dir = resolveSessionDir();
      if (!Files.isDirectory(dir)) {
        Files.createDirectories(dir);
      }
      setOwnerOnly(dir, "rwx------");
      Path target = dir.resolve("session-" + httpPort + ".json");
      Path tmp = Files.createTempFile(dir, "session-", ".tmp");
      setOwnerOnly(tmp, "rw-------");
      String json =
          "{\"port\":"
              + httpPort
              + ",\"token\":\""
              + getToken()
              + "\",\"pid\":"
              + ProcessHandle.current().pid()
              + "}\n";
      Files.writeString(tmp, json, StandardCharsets.UTF_8);
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      sessionFile = target;
    } catch (IOException | UnsupportedOperationException e) {
      LOG.debugf("Could not write Archlens session file: %s", e.getMessage());
    }
  }

  private static void setOwnerOnly(Path path, String perms) {
    try {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(perms));
    } catch (IOException | UnsupportedOperationException e) {
      // Non-POSIX filesystem (e.g. Windows): rely on the user profile ACLs
      LOG.debugf("Could not restrict permissions on %s: %s", path, e.getMessage());
    }
  }
}
