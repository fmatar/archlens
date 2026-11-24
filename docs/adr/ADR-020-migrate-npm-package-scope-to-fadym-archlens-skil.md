---
id: ADR-020
title: Migrate NPM Package Scope to @fadym/archlens-skill with OIDC Trusted Publishing
status: accepted
scope: technical
ticket: #247
supersedes: ADR-019
createdAt: 2026-11-24T16:01:21.148Z
---

# ADR-020: Migrate NPM Package Scope to @fadym/archlens-skill with OIDC Trusted Publishing

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #247
- **Supersedes / Amends**: ADR-019

## 1. Context & Spec Drift Trigger
Closure of the legacy slixes npm account on 2026-11-23 orphaned @fmatar/archlens-skill under npm-support and revoked static NPM_TOKEN credentials.

## 2. Decision
Rename cli/package.json to @fadym/archlens-skill and root package.json to @fadym/archlens under the active fadym npmjs.com account, update set-version.js, MCP manifests, skills, and docs, remove dead GitHub Packages npm mirrors, and publish via GitHub Actions OIDC Trusted Publishing (id-token: write, npm >= 11.5.1) with SLSA --provenance.

## 3. Consequences & Downstream Impact
Eliminates long-lived NPM_TOKEN secrets, attaches cryptographic SLSA build provenance to every @fadym/archlens-skill release and canary publish, and preserves fmatar/archlens for GitHub, GHCR, and GitHub Actions.
