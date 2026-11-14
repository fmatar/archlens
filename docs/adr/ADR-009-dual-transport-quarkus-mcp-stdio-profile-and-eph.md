---
id: ADR-009
title: Dual-Transport Quarkus MCP STDIO Profile and Ephemeral Docker Container Support
status: accepted
scope: technical
ticket: ##8714
supersedes: 
createdAt: 2026-11-14T04:40:52.621Z
---

# ADR-009: Dual-Transport Quarkus MCP STDIO Profile and Ephemeral Docker Container Support

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: ##8714
- **Supersedes / Amends**: None

## 1. Context & Spec Drift Trigger
backend/pom.xml previously shipped only quarkus-mcp-server-http, preventing ghcr.io/fmatar/archlens from running as a pure stdin/stdout MCP container via docker run -i --rm like ghcr.io/fmatar/quarkus-kb.

## 2. Decision
Add io.quarkiverse.mcp:quarkus-mcp-server-stdio alongside quarkus-mcp-server-http in backend/pom.xml, keep STDIO disabled by default for HTTP/UI and Surefire tests, expose a runtime %mcp-stdio profile (-e QUARKUS_PROFILE=mcp-stdio) that enables STDIO with null-system-out and stderr logging, extend WorkspacePathResolver for ARCHLENS_HOST_WORKSPACE and Linux/macOS host paths, and add --docker MCP manifest generation in cli/src/mcp-registry.js.

## 3. Consequences & Downstream Impact
Single container image supports both persistent Web Workbench + Streamable HTTP MCP (:8088) and zero-Node/zero-Java ephemeral docker run -i --rm MCP STDIO execution across Claude, Antigravity, Cursor, and VS Code.
