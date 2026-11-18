package io.slixes.archlens.delivery.rest;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP filter requiring the per-launch session token on {@code /api/*} and {@code /mcp}.
 *
 * <p>Accepted credentials: {@code Authorization: Bearer <token>}, {@code X-Archlens-Token:
 * <token>}, or the {@code archlens_session_<port>} cookie. Opening the workbench UI with {@code
 * ?token=<token>} sets that cookie (HttpOnly, SameSite=Strict) and redirects to the same page
 * without the token, so the bundled UI, its {@code fetch} calls and its EventSource stream work
 * without further changes. {@code /api/version} stays open as a liveness probe, and {@code /q/*}
 * (health) and static UI assets are not guarded.
 */
@ApplicationScoped
public class SessionTokenFilter {

  static final String TOKEN_HEADER = "X-Archlens-Token";
  static final String TOKEN_QUERY_PARAM = "token";
  private static final int FILTER_PRIORITY = 10_000;

  @Inject SessionTokenService sessionTokens;

  public SessionTokenFilter() {}

  public SessionTokenFilter(SessionTokenService sessionTokens) {
    this.sessionTokens = sessionTokens;
  }

  void register(@Observes Filters filters) {
    filters.register(this::handle, FILTER_PRIORITY);
  }

  /**
   * Percent-decodes {@code rawPath} once, strips RFC 3986 matrix parameters ({@code ;[^/]*}) from
   * every path segment, collapses duplicate slashes, and lowercases using {@link Locale#ROOT}.
   */
  static String canonicalizePath(String rawPath) {
    if (rawPath == null) {
      return null;
    }
    String decoded = percentDecodeOnce(rawPath);
    String withoutMatrix = decoded.replaceAll(";[^/]*", "");
    String collapsed = withoutMatrix.replaceAll("/{2,}", "/");
    return collapsed.toLowerCase(Locale.ROOT);
  }

  private static String percentDecodeOnce(String s) {
    if (s.indexOf('%') < 0) {
      return s;
    }
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == '%' && i + 2 < bytes.length) {
        int hi = Character.digit((char) bytes[i + 1], 16);
        int lo = Character.digit((char) bytes[i + 2], 16);
        if (hi >= 0 && lo >= 0) {
          out.write((hi << 4) | lo);
          i += 2;
          continue;
        }
      }
      out.write(bytes[i]);
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  /** Paths that require the session token. */
  static boolean isProtectedPath(String path) {
    if (path == null) {
      return false;
    }
    if ("/api/version".equals(path)) {
      return false;
    }
    String canonical = canonicalizePath(path);
    return "/api".equals(canonical)
        || canonical.startsWith("/api/")
        || "/mcp".equals(canonical)
        || canonical.startsWith("/mcp/");
  }

  static String extractHeaderToken(String authHeader, String tokenHeader) {
    if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return authHeader.substring(7).trim();
    }
    if (tokenHeader != null && !tokenHeader.isBlank()) {
      return tokenHeader.trim();
    }
    return null;
  }

  static String unauthorizedJson() {
    return new JsonObject(
            Map.of(
                "error",
                "unauthorized",
                "message",
                "Archlens session token required. Open the workbench with the URL"
                    + " printed at startup (it contains ?token=...), or send the"
                    + " token in the X-Archlens-Token header."))
        .encode();
  }

  /** Extracts the presented token from the Authorization header, custom header, or cookie. */
  String presentedToken(RoutingContext rc) {
    String headerToken =
        extractHeaderToken(
            rc.request().getHeader("Authorization"), rc.request().getHeader(TOKEN_HEADER));
    if (headerToken != null) {
      return headerToken;
    }
    Cookie cookie = rc.request().getCookie(sessionTokens.cookieName());
    return cookie != null ? cookie.getValue() : null;
  }

  void handle(RoutingContext rc) {
    if (!sessionTokens.isEnabled() || rc.request().method() == HttpMethod.OPTIONS) {
      rc.next();
      return;
    }
    String path = rc.normalizedPath();
    String rawRequestPath = rc.request().path();
    if (isProtectedPath(path) || isProtectedPath(rawRequestPath)) {
      if (sessionTokens.matches(presentedToken(rc))) {
        rc.next();
      } else {
        rc.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .putHeader("WWW-Authenticate", "Bearer realm=\"archlens\"")
            .end(unauthorizedJson());
      }
      return;
    }

    String queryToken = rc.request().getParam(TOKEN_QUERY_PARAM);
    if (queryToken != null
        && rc.request().method() == HttpMethod.GET
        && sessionTokens.matches(queryToken)) {
      rc.response()
          .addCookie(
              Cookie.cookie(sessionTokens.cookieName(), sessionTokens.getToken())
                  .setPath("/")
                  .setHttpOnly(true)
                  .setSameSite(CookieSameSite.STRICT))
          .setStatusCode(302)
          .putHeader("Location", stripTokenParam(path, rc.request().query()))
          .putHeader("Cache-Control", "no-store")
          .end();
      return;
    }
    rc.next();
  }

  /** Rebuilds {@code path?query} without the token parameter. */
  static String stripTokenParam(String path, String query) {
    String base = path == null || !path.startsWith("/") || path.startsWith("//") ? "/" : path;
    if (query == null || query.isEmpty()) {
      return base;
    }
    StringBuilder kept = new StringBuilder();
    for (String pair : query.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
      if (TOKEN_QUERY_PARAM.equals(key)
          || TOKEN_QUERY_PARAM.equals(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8))) {
        continue;
      }
      if (kept.length() > 0) {
        kept.append('&');
      }
      kept.append(pair);
    }
    return kept.length() == 0 ? base : base + "?" + kept;
  }
}
