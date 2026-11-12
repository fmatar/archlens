---
id: ADR-005
title: Quarkus 3.40.1.2, MCP 2.0.4 & Latest GA Dependency Alignment
status: accepted
scope: technical
ticket: #6157
supersedes: CAP-01, CAP-02, CAP-03, CAP-04
createdAt: 2026-11-12T10:37:04.753Z
---

# ADR-005: Quarkus 3.40.1.2, MCP 2.0.4 & Latest GA Dependency Alignment

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #6157
- **Supersedes / Amends**: CAP-01, CAP-02, CAP-03, CAP-04

## 1. Context & Spec Drift Trigger
Before cutting patch release v1.1.1, backend Maven BOMs/plugins, Quinoa managed Node/pnpm versions, and Svelte 5 frontend dependencies had newer General Availability (GA) releases while several upstream projects published pre-release candidates (Quarkus 4.0.0.CR1, MCP 2.1.0.Beta1, Maven Compiler 4.0.0-beta-5).

## 2. Decision
Upgrade all backend and frontend manifests strictly to latest GA versions (Quarkus BOM 3.40.1.2, Quarkus MCP Server BOM 2.0.4, Surefire 3.6.1, JaCoCo 0.8.16, Enforcer 3.7.0, Spotless 3.11.0 with google-java-format 2.0.0, FindSecBugs 1.14.0, Resources 3.5.0, Versions 2.25.1, Quinoa Node 26.13.1 & pnpm 12.18.0, @lucide/svelte 1.72.0, @tsconfig/svelte 5.0.9, jsdom 31.0.0, Vite 8.3.8) while retaining TypeScript 6.0.3 for svelte-check peer compatibility and extending scripts/update-dependencies.js for 4-segment micro SemVer and full pom.xml coverage.

## 3. Consequences & Downstream Impact
Zero pre-release dependencies enter v1.1.1; Java sources conform to google-java-format 2.0.0; scheduled dependency-pulse runs now track all 18 Maven properties and 4-segment Quarkus micro releases accurately.
