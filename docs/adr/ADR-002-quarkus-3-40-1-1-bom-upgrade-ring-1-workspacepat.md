---
id: ADR-002
title: Quarkus 3.40.1.1 BOM Upgrade, Ring 1 WorkspacePathResolver Binding & MCP Hibernate Validator
status: rejected
scope: technical
ticket: #192
supersedes: CAP-01, CAP-02, CAP-03, CAP-04
createdAt: 2026-11-06T23:52:46.498Z
---

# ADR-002: Quarkus 3.40.1.1 BOM Upgrade, Ring 1 WorkspacePathResolver Binding & MCP Hibernate Validator

- **Status**: `rejected`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #192
- **Supersedes / Amends**: CAP-01, CAP-02, CAP-03, CAP-04

## 1. Context & Spec Drift Trigger

A live audit of the repository using `quarkus-mcp` (`quarkus_audit_project`) and `archlens` (`inspectArchitecture`) identified three architectural and build hygiene improvements tracked under Issues #190, #191, and #192:
1. Root `pom.xml` pinned `quarkus.platform.version` at `3.40.1` instead of the latest patch release `3.40.1.1`.
2. `DiagramResource` and `MailboxResource` in `io.slixes.archlens.delivery.rest` implicitly resolved the `@Deprecated` same-package `WorkspacePathResolver` shim instead of importing the Ring 1 `io.slixes.archlens.usecase.WorkspacePathResolver` use case.
3. `backend/pom.xml` and `ArchlensMcpService` lacked `quarkus-mcp-server-hibernate-validator` and declarative `@NotBlank` Bean Validation constraints on mandatory MCP `@ToolArg` and `@PromptArg` parameters.

## 2. Decision

1. **Ticket #190**: Upgrade `<quarkus.platform.version>` in root `pom.xml` to `3.40.1.1` and align container image OCI labels in `application.properties` to `quarkus.container-image.labels.*`.
2. **Ticket #191**: Bind `DiagramResource` and `MailboxResource` directly to `io.slixes.archlens.usecase.WorkspacePathResolver` (Ring 1 Application layer) and eliminate all `javac` deprecation warnings.
3. **Ticket #192**: Add `io.quarkiverse.mcp:quarkus-mcp-server-hibernate-validator` to `backend/pom.xml` and annotate all mandatory MCP `@ToolArg` and `@PromptArg` parameters in `ArchlensMcpService` with Jakarta Bean Validation `@NotBlank`.

## 3. Consequences & Downstream Impact

- **Zero Concentric Ring Violations**: Level 3 REST and MCP adapters depend strictly inward on Level 1 (`usecase`, `engine`) and Level 0 (`domain`).
- **Automated MCP Parameter Validation**: Invalid or blank tool/prompt arguments are rejected at the Quarkus MCP protocol boundary before invoking Ring 1 use cases.
- **Zero Deprecation Warnings**: Backend compilation completes with zero `javac` deprecation warnings while retaining `92% (Grade A)` Architecture Fitness Index and `87.0%` JaCoCo coverage.

## 4. Governance Review Decision
- **Decision**: `ACCEPTED`
- **Reviewer**: @sdlc-gate
- **Rationale**: Approved via Quarkus MCP + Archlens audit remediation (#190, #191, #192)
- **Decided At**: 2026-11-06T23:53:00.000Z

## 4. Governance Review Decision
- **Decision**: `REJECTED`
- **Reviewer**: @human-operator
- **Rationale**: Reviewed via SDLC Governance Gate
- **Decided At**: 2026-11-06T23:53:36.514Z
