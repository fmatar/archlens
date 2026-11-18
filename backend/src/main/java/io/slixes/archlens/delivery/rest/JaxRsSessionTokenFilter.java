package io.slixes.archlens.delivery.rest;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Defense-in-depth JAX-RS {@link PreMatching} filter enforcing the session token on the exact
 * post-normalization path seen by RESTEasy Reactive routing, complementing {@link
 * SessionTokenFilter}.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class JaxRsSessionTokenFilter implements ContainerRequestFilter {

  @Inject SessionTokenService sessionTokens;

  public JaxRsSessionTokenFilter() {}

  public JaxRsSessionTokenFilter(SessionTokenService sessionTokens) {
    this.sessionTokens = sessionTokens;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (sessionTokens == null
        || !sessionTokens.isEnabled()
        || "OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
      return;
    }

    String rawPath = requestContext.getUriInfo().getRequestUri().getRawPath();
    String path = requestContext.getUriInfo().getPath();
    if (path != null && !path.startsWith("/")) {
      path = "/" + path;
    }

    boolean isExactVersionGet =
        "GET".equalsIgnoreCase(requestContext.getMethod())
            && "/api/version".equals(path)
            && "/api/version".equals(rawPath);
    if (isExactVersionGet) {
      return;
    }

    if (SessionTokenFilter.isProtectedPath(path) || SessionTokenFilter.isProtectedPath(rawPath)) {
      String presented = presentedToken(requestContext);
      if (!sessionTokens.matches(presented)) {
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .header("WWW-Authenticate", "Bearer realm=\"archlens\"")
                .entity(SessionTokenFilter.unauthorizedJson())
                .build());
      }
    }
  }

  String presentedToken(ContainerRequestContext ctx) {
    String headerToken =
        SessionTokenFilter.extractHeaderToken(
            ctx.getHeaderString("Authorization"),
            ctx.getHeaderString(SessionTokenFilter.TOKEN_HEADER));
    if (headerToken != null) {
      return headerToken;
    }
    Cookie cookie = ctx.getCookies().get(sessionTokens.cookieName());
    return cookie != null ? cookie.getValue() : null;
  }
}
