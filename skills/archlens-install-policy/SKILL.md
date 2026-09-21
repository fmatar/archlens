---
name: archlens-install-policy
description: Inspects any target codebase, detects programming languages and packages, and scaffolds an Archlens Clean Architecture policy (.uml-viewer/policy.json) and agent workbench companion integration.
version: 1.0.0
license: Apache-2.0
---

# Archlens Policy & Companion Installer (`archlens-install-policy`)

Use this skill to initialize Clean Architecture governance in any target software project for the **Archlens Dynamic Clean Architecture Workbench**.

This skill analyzes the project's build system and directory layout, infers common package prefixes, organizes architectural packages into concentric rings, generates `.uml-viewer/policy.json`, configures workbench connectivity, and equips AI coding assistants (Claude Code, Gemini CLI, Google Antigravity) with mailbox refactoring protocols.

---

## 🎯 When to Use This Skill
Trigger this skill whenever the user asks to:
- "Install Archlens policy in this project"
- "Setup Archlens" or "Configure Clean Architecture policy"
- "Initialize .uml-viewer/policy.json"
- "Connect repository to Archlens workbench"
- "Prepare this repository for Archlens refactoring companion"

---

## 📋 Core Deliverables
When executed, this skill generates the following artifacts in the target project root:
1. **`.uml-viewer/policy.json`**:
   - Declares the project title, source root, and common package prefix.
   - Organizes discovered packages into concentric tiers:
     - **Level 0 (Domain Core)**: Entities, value objects, domain logic (`domain`, `model`, `entities`, `core`).
     - **Level 1 (Application)**: Use cases, interaction services, application ports (`usecase`, `application`, `service`, `port`).
     - **Level 2 (Adapters)**: Controllers, presenters, gateways, repository implementations (`adapter`, `controller`, `gateway`, `presenter`, `repository`, `dto`).
     - **Level 3 (Infrastructure)**: Databases, web frameworks, persistence drivers, configuration (`infrastructure`, `config`, `db`, `web`).
2. **`.uml-viewer/workbench.config.json`**:
   - Configures the Archlens server URL (`http://localhost:8088`) and mailbox version.
3. **`CLAUDE.md` and `AGENTS.md`**:
   - Injects the Clean Architecture Companion Protocol to instruct agents how to handle inbound mailbox tasks (`REGEN`, `APPLY_PROPOSAL`, `REFRESH_CRAP`) and enforce the Dependency Inversion Principle.

---

## 🛠️ Execution Instructions for Agents

### Step 1: Execute Scaffolding Script
Run the bundled zero-dependency Python script against the target repository:

```bash
python3 /Users/fady/workspace/labs/archlens/skills/archlens-install-policy/scripts/init_policy.py --path .
```

*Flags available:*
- `--path <DIR>`: Target project directory (defaults to current directory).
- `--title <NAME>`: Custom project display title.
- `--prefix <PKG>`: Explicit package prefix (e.g. `com.mycompany.service`).
- `--server-url <URL>`: Archlens workbench endpoint (defaults to `http://localhost:8088`).
- `--force`: Overwrite existing `.uml-viewer/policy.json` if present.

### Step 2: Review Generated Policy
Inspect the generated `.uml-viewer/policy.json` to confirm the packages align with the developer's architecture intent. If the project uses custom domain terminology, adjust the `levels` array accordingly.

### Step 3: Verify with Archlens Workbench
Once configured, developers can open the project in the visual workbench:
```text
http://localhost:8088/?projectRoot=<ABSOLUTE_PATH_TO_PROJECT>
```
Or open Archlens and use `⌘O` / `Ctrl+O` to select the project via the visual filesystem explorer.
