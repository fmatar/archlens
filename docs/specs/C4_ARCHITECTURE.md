# C4 Architecture Specification: Clean Architecture Dynamic UML Viewer

## 1. Context Diagram

```mermaid
flowchart TD
    User["Software Architect / Developer"]
    Agent["Autonomous AI Agent\n(Antigravity / Claude / Cursor)"]
    
    subgraph System ["UML Architecture Workbench"]
        App["Dynamic Clean Architecture UML Viewer\n(Java 25 Quarkus + Svelte 5)"]
    end
    
    TargetCode["Examined Java Codebase\n(src/main/java)"]
    MetricsData["Test & Coverage Snapshots\n(JaCoCo & PITest)"]
    MailboxFile[".uml-viewer/ Mailbox\n(Atomic JSON Queues)"]

    User -->|Interacts, Drills down, Plays What-If| App
    App -->|Scans AST & Topology| TargetCode
    App -->|Overlays CRAP & Mutation| MetricsData
    App <-->|Appends user commands & receives updates| MailboxFile
    Agent <-->|Pops commands, refactors code, signals REGEN| MailboxFile
    Agent -->|Edits code & runs tests| TargetCode
```

## 2. Container Diagram

```mermaid
flowchart TD
    subgraph FrontendContainer ["Frontend Web Client (Svelte 5 Runes)"]
        UI["SPA / Desktop UI\n(Canvas, Inspector, Class Cards)"]
        State["diagram.svelte.ts\n(Reactive Signals: zoom, pan, proposals)"]
        SSEListener["EventSource Client\n(Listens for live-reload pushes)"]
    end

    subgraph BackendContainer ["Backend Engine (Quarkus on Java 25)"]
        API["REST & SSE Gateway\n(RESTEasy Reactive)"]
        Scanner["JavaParser AST Scanner\n(Extracts types, methods, fields, deps)"]
        RuleEngine["Clean Architecture Policy Engine\n(Validates levels: inner -> outer violations)"]
        MetricsEngine["Metrics Ingestion Engine\n(Computes CRAP = CC^2*(1-Cov)^3 + CC & Mutation stats)"]
        MailboxSvc["FileMailboxService & Watcher\n(NIO WatchService on .uml-viewer/)"]
    end

    UI --> State
    State --> SSEListener
    SSEListener <-->|SSE / WebSockets| API
    UI -->|REST API calls| API
    API --> RuleEngine
    API --> Scanner
    API --> MetricsEngine
    API --> MailboxSvc
```

## 3. Component Contract & Domain Model

- **`ClassNode`**: Record representing a compiled Java type with its stereotype (`CLASS`, `INTERFACE`, `RECORD`, `ENUM`), package name, fields, and method metrics.
- **`ComponentNode`**: Hierarchical namespace/package grouping with computed health scores (CRAP μ, max, σ, and Mutation killed/survived ratios).
- **`DependencyEdge`**: Edge connecting two nodes (`from`, `to`, `kind`, `isViolating`).
- **`DependencyRuleValidator`**: Verifies that inner layers (lower numerical rank, e.g. Domain = 0) do not depend on outer layers (higher rank, e.g. Adapters = 2).
