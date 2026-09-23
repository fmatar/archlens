---
name: archlens-install-policy
description: Inspects any target codebase, detects programming languages and packages, and scaffolds an Archlens Clean Architecture policy (.archlens/policy.json) and agent workbench companion integration.
version: 1.1.0
license: Apache-2.0
---

# Archlens Policy & Companion Installer (`archlens-install-policy`)

Use this skill to initialize Clean Architecture governance in any target software project for the **Archlens Dynamic Clean Architecture Workbench**.

This skill analyzes the project build system and directory layout, infers common package prefixes, organizes architectural packages into concentric rings, generates `.archlens/policy.json`, configures workbench connectivity, and equips AI coding assistants (Claude Code, Gemini CLI, Google Antigravity) with mailbox refactoring protocols.

---

## 🎯 When to Use This Skill
Trigger this skill whenever the user asks to:
- "Install Archlens policy in this project"
- "Setup Archlens" or "Configure Clean Architecture policy"
- "Initialize .archlens/policy.json"
- "Upgrade Archlens config from .uml-viewer to .archlens"
- "Connect repository to Archlens workbench"
- "Prepare this repository for Archlens refactoring companion"

---

## 📋 Core Deliverables
When executed, this skill generates the following artifacts in the target project root:
1. **`.archlens/policy.json`**:
   - Declares the project title, source root, and common package prefix.
   - Organizes discovered packages into concentric tiers:
     - **Level 0 (Domain Core)**: Entities, value objects, domain logic (`domain`, `model`, `entities`, `core`).
     - **Level 1 (Application)**: Use cases, interaction services, application ports (`usecase`, `application`, `service`, `port`).
     - **Level 2 (Adapters)**: Controllers, presenters, gateways, repository implementations (`adapter`, `controller`, `gateway`, `presenter`, `repository`, `dto`).
     - **Level 3 (Infrastructure)**: Databases, web frameworks, persistence drivers, configuration (`infrastructure`, `config`, `db`, `web`).
2. **`.archlens/workbench.config.json`**:
   - Configures the Archlens server URL (`http://localhost:8088`) and mailbox version.
3. **`CLAUDE.md` and `AGENTS.md`**:
   - Injects the Clean Architecture Companion Protocol to instruct agents how to handle inbound mailbox tasks (`REGEN`, `APPLY_PROPOSAL`, `REFRESH_CRAP`) and enforce the Dependency Inversion Principle.

> [!NOTE]
> Backward Compatibility: Projects with existing `.uml-viewer/` configurations continue to function without modification. Upgrading to `.archlens/` is recommended for standard alignment.

---

## 🛠️ Execution Instructions for Agents

### Step 1: Run the Archlens Installer or Upgrader
Execute the cross-platform CLI via NPX against the target repository:

```bash
# Initialize a new project with defaults
npx @fmatar/archlens-skill --path . --yes

# Or upgrade an existing project from .uml-viewer to .archlens
npx @fmatar/archlens-skill upgrade --path .
```

*Flags available:*
- `--path <DIR>`: Target project directory (defaults to current directory).
- `--title <NAME>`: Custom project display title.
- `--prefix <PKG>`: Explicit package prefix (e.g. `com.mycompany.service`).
- `--server-url <URL>`: Archlens workbench endpoint (defaults to `http://localhost:8088`).
- `--update`: Re-scans code for new packages and updates existing policy.
- `--upgrade`: Migrates legacy `.uml-viewer/` configuration and mailboxes to `.archlens/`.
- `--force`: Overwrite existing `.archlens/policy.json` if present.

Alternatively, agents can run the bundled Python script:
```bash
python3 scripts/init_policy.py --path .

# Or upgrade:
python3 scripts/init_policy.py --path . --upgrade
```

### Step 2: Review Generated Policy
Inspect the generated `.archlens/policy.json` to confirm the packages align with the developer's architecture intent. If the project uses custom domain terminology, adjust the `levels` array accordingly.

### Step 3: Verify with Archlens Workbench
Once configured, developers can open the project in the visual workbench:
```text
http://localhost:8088/?projectRoot=<ABSOLUTE_PATH_TO_PROJECT>
```
Or open Archlens and use `⌘O` / `Ctrl+O` to select the project via the visual filesystem explorer.
