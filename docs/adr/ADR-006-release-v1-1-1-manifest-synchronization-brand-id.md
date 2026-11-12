---
id: ADR-006
title: Release v1.1.1 — Manifest Synchronization, Brand Identity & GA Dependency Alignment
status: accepted
scope: technical
ticket: #222
supersedes: CAP-01, CAP-02, CAP-03, CAP-04
createdAt: 2026-11-12T11:25:42.483Z
---

# ADR-006: Release v1.1.1 — Manifest Synchronization, Brand Identity & GA Dependency Alignment

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #222
- **Supersedes / Amends**: CAP-01, CAP-02, CAP-03, CAP-04

## 1. Context & Spec Drift Trigger
Cutting patch release v1.1.1 consolidating self-hosted npm/canary publishing fixes (#4772), Hexagonal Dependency Vault brand identity and 10-30-90 documentation (#6538), and latest GA backend/frontend dependency upgrades (#6157 / ADR-005).

## 2. Decision
Synchronize all 15 Maven, npm, Quarkus, MCP, REST, skill, and workflow version manifests to 1.1.1, roll CHANGELOG.md ([1.1.1] - 2026-11-12), and generate cryptographic delivery regression proof.

## 3. Consequences & Downstream Impact
Tag v1.1.1 builds and publishes verified multi-arch GHCR container images, linux-amd64 GraalVM native binary, and @fmatar/archlens-skill@1.1.1 to public npm and GitHub Packages.
