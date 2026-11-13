# 🛠️ Skills & CLI Reference

Canonical skills: `sdlc`, `sdlc-adr`, `sdlc-doctor`, `sdlc-flow`, `sdlc-gate`, `sdlc-product`, `sdlc-release`, `sdlc-status`, `sdlc-tdd`, `sdlc-wiki`.

Archlens ships a dual skill ecosystem: **end-user architectural governance skills** distributed via `npx @fmatar/archlens-skill` (and `npx skills add fmatar/archlens`) and **autonomous SDLC orchestration skills** housed in `.agent/skills/` and driven by `.agent/scripts/sdlc-engine.mjs`.

---

## 🏛️ 1. End-User Architectural Governance Skills (`skills/`)

| Skill ID | Path | Purpose |
| :--- | :--- | :--- |
| `archlens` | `skills/archlens/SKILL.md` | Full-stack Clean Architecture auditor, headless CI fitness gate (`check`), polyglot Surgical DIP Inverter (`synthesizeDipInversion`), and MCP companion (`npx @fmatar/archlens-skill`). |
| `archlens-install-policy` | `skills/archlens-install-policy/SKILL.md` | Bootstraps `.archlens/policy.json` concentric rings and updates `AGENTS.md` / `CLAUDE.md` with Clean Architecture companion protocols. |
| `uml-workbench-companion` | `skills/uml-workbench-companion/SKILL.md` | Monitors `.archlens/to-agent.json` and `.archlens/to-viewer.json` mailbox queues to execute `REGEN`, `APPLY_PROPOSAL`, `INVERT_DEPENDENCY`, and `REFRESH_CRAP` directives. |

---

## ⚙️ 2. Canonical SDLC Orchestration Skills (`.agent/skills/`)

| Skill | Slash Command | Engine Mapping (`sdlc-engine.mjs`) | Responsibility |
| :--- | :--- | :--- | :--- |
| `sdlc` | `/sdlc <args>` | Universal 1:1 router | Routes any subcommand (`doctor`, `begin`, `status`, `drift`, `pre-pr`, `gate`, `kanban`, `ui`, `wiki`) to `.agent/scripts/sdlc-engine.mjs`. |
| `sdlc-doctor` | `/sdlc doctor` | `doctor [--fix]` | Stack detection (`maven`, `node`, `cargo`, `go`, `python`), 4-Layer Git Guard hook installation, and `.archlens/policy.json` scaffolding. |
| `sdlc-product` | `/sdlc product` | `product parse \| plan \| run` | Synthesizes `PRODUCT.md` (`INV-XX`, `CAP-XX`, `SPEC-XX`), authors specifications in `docs/specs/`, and executes topological DAG waves. |
| `sdlc-flow` | `/sdlc begin` | `stage begin \| pre-pr \| complete` | Drives 8-stage feature/fix lifecycles, branch checkout, `docs/KANBAN.md` sync, and architectural traceability. |
| `sdlc-tdd` | `/sdlc tdd` | `test` | Guides Robert C. Martin Red-Green-Refactor TDD, Transformation Priority Premise (TPP), and Dual-Gate coverage ($\ge 85\%$) & mutation ($\ge 80\%$) verification. |
| `sdlc-adr` | `/sdlc adr` | `adr create \| list \| status`, `drift check` | Mints and manages ADRs in `docs/adr/`, synchronizes `docs/DECISION_MAP.md`, and enforces the Spec-Drift Sentinel (`INV-03`). |
| `sdlc-gate` | `/sdlc gate` | `gate [--issue #N]` | Runs the 9-pillar quality gate, Archlens concentric ring check, secret scan, and issues a cryptographically hashed Stage 7 gate receipt. |
| `sdlc-status` | `/sdlc status` | `status`, `arch diff`, `ui` | Displays active SDLC session progress, spec-drift status, `docs/KANBAN.md` state, and the interactive Architecture Diff UI. |
| `sdlc-wiki` | `/sdlc wiki` | `wiki sync \| audit [--strict]` | Synchronizes `wiki/Codebase-Map.md` with Java, Svelte 5, TypeScript, and Node.js AST modules and audits `wiki/*.md` for zero broken paths or stale references. |
| `sdlc-release` | `/sdlc release` | `release` | Synchronizes `README.md`, `CHANGELOG.md`, `docs/specs/`, and `wiki/`, and automates SemVer tagging and release verification. |

---

## 💻 3. CLI Reference (`npx @fmatar/archlens-skill`)

```bash
# Install all 3 Archlens skills directly via open Skills CLI (skills.sh)
npx skills add fmatar/archlens --all -y

# Or scaffold policy + install skills + register MCP in one command
npx @fmatar/archlens-skill --yes --skills --mcp
npx @fmatar/archlens-skill skills [-g] [--agent claude-code,cursor]

# Headless Clean Architecture CI Quality Gate
npx @fmatar/archlens-skill check [dir] \
  --max-violations 0 \
  --detect-cycles \
  --screaming-threshold 60 \
  --suggest-features \
  --fitness-threshold 70 \
  --format sarif -O archlens-results.sarif

# Launch the Interactive Workbench (Native Binary -> Runner JAR -> Docker Fallback)
npx @fmatar/archlens-skill start [dir] [-p 8088]

# Run the MCP Server over Stdio (4-Tier Auto-Resurrection Waterfall)
npx @fmatar/archlens-skill mcp [dir]
```

---

## Source & Engine Modules
`.agent/scripts/sdlc-engine.mjs`, `.agent/scripts/lib/adr-drift.mjs`, `.agent/scripts/lib/arch-trace.mjs`, `.agent/scripts/lib/audit-chain.mjs`, `.agent/scripts/lib/cache.mjs`, `.agent/scripts/lib/core.mjs`, `.agent/scripts/lib/decision-graph.mjs`, `.agent/scripts/lib/dossier.mjs`, `.agent/scripts/lib/git-guard.mjs`, `.agent/scripts/lib/installer.mjs`, `.agent/scripts/lib/pm-provider.mjs`, `.agent/scripts/lib/product-dag.mjs`, `.agent/scripts/lib/quality-gate.mjs`, `.agent/scripts/lib/session.mjs`, `.agent/scripts/lib/wiki-sync.mjs`, `.agent/scripts/lib/workbench-server.mjs`
