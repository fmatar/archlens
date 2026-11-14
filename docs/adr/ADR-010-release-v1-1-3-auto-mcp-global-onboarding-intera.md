---
id: ADR-010
title: Release v1.1.3 — Auto-MCP Global Onboarding, Interactive Docker Transport Choice & Documentation Parity
status: accepted
scope: technical
ticket: #8454
supersedes: ADR-009
createdAt: 2026-11-14T18:11:28.469Z
---

# ADR-010: Release v1.1.3 — Auto-MCP Global Onboarding, Interactive Docker Transport Choice & Documentation Parity

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #8454
- **Supersedes / Amends**: ADR-009

## 1. Context & Spec Drift Trigger
Global skill installs and npx skills onboarding did not auto-register MCP manifests, interactive CLI lacked Docker vs NPX transport selection, and v1.1.2 on main predates %mcp-stdio (#8714).

## 2. Decision
Auto-register global-scoped MCP manifests on global installs (--no-mcp opt-out), prompt NPX vs ephemeral Docker mcp-stdio in interactive TTY when Docker is detected, add First-Run MCP Bootstrap to skills, synchronize README/Wiki/llms.txt, and cut release v1.1.3.

## 3. Consequences & Downstream Impact
Tag v1.1.3 publishes @fmatar/archlens-skill@1.1.3 and multi-arch ghcr.io/fmatar/archlens:latest with %mcp-stdio support, keeping repo-scoped .mcp.json portable by default while equipping global workstations in one command.
