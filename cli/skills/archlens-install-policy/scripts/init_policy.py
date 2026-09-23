#!/usr/bin/env python3
"""
Archlens Policy & Companion Initialization Script
Zero-dependency CLI tool to inspect any project codebase and scaffold:
  1. .archlens/policy.json (Clean Architecture concentric tiers)
  2. .archlens/workbench.config.json (Visual workbench service settings)
  3. CLAUDE.md & AGENTS.md companion guidelines (Mailbox IPC protocol)
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path


def detect_project_language(root: Path) -> str:
    if (root / "pom.xml").exists() or (root / "build.gradle").exists() or (root / "build.gradle.kts").exists():
        if list(root.glob("**/src/main/kotlin")):
            return "kotlin"
        return "java"
    if (root / "package.json").exists() or (root / "tsconfig.json").exists():
        return "typescript"
    if (root / "pyproject.toml").exists() or (root / "setup.py").exists() or (root / "requirements.txt").exists():
        return "python"
    if (root / "Cargo.toml").exists():
        return "rust"
    if (root / "go.mod").exists():
        return "go"
    if (root / "project.clj").exists() or (root / "deps.edn").exists():
        return "clojure"
    return "java"


def detect_source_root(root: Path, lang: str) -> str:
    candidates = []
    if lang in ("java", "kotlin"):
        candidates = ["src/main/java", "src/main/kotlin", "backend/src/main/java", "core/src/main/java", "src"]
    elif lang == "typescript":
        candidates = ["src", "lib", "app", "frontend/src"]
    elif lang == "python":
        candidates = ["src", "app", "lib", "."]
    elif lang == "rust":
        candidates = ["src"]
    elif lang == "go":
        candidates = ["pkg", "internal", "cmd", "."]
    elif lang == "clojure":
        candidates = ["src", "src/clj"]

    for candidate in candidates:
        candidate_path = root / candidate
        if candidate_path.exists() and candidate_path.is_dir():
            return candidate

    return "src"


def find_common_package_prefix(source_dir: Path) -> str:
    if not source_dir.exists():
        return ""

    java_files = list(source_dir.glob("**/*.java"))
    if not java_files:
        kt_files = list(source_dir.glob("**/*.kt"))
        if kt_files:
            java_files = kt_files

    packages = []
    for jf in java_files[:100]:
        try:
            content = jf.read_text(encoding="utf-8", errors="ignore")
            match = re.search(r"^\s*package\s+([a-zA-Z0-9_.]+)\s*;", content, re.MULTILINE)
            if match:
                packages.append(match.group(1))
        except Exception:
            continue

    if not packages:
        return ""

    split_pkgs = [p.split(".") for p in packages]
    common = []
    for parts in zip(*split_pkgs):
        if len(set(parts)) == 1:
            common.append(parts[0])
        else:
            break

    known_layer_terms = {
        "domain", "model", "models", "entity", "entities", "core",
        "application", "app", "usecase", "usecases", "service", "services", "port", "ports",
        "adapter", "adapters", "controller", "controllers", "gateway", "gateways",
        "presenter", "presenters", "repository", "repositories", "resource", "resources", "api", "dto",
        "infrastructure", "infra", "persistence", "db", "database", "config", "configuration", "web", "server"
    }
    while common and common[-1].lower() in known_layer_terms:
        common.pop()

    return ".".join(common)


def discover_subpackages(source_dir: Path, prefix: str) -> list[str]:
    if not source_dir.exists():
        return []

    prefix_parts = prefix.split(".") if prefix else []
    search_root = source_dir
    for part in prefix_parts:
        candidate = search_root / part
        if candidate.exists() and candidate.is_dir():
            search_root = candidate
        else:
            break

    subpackages = []
    for item in sorted(search_root.iterdir()):
        if item.is_dir() and not item.name.startswith("."):
            subpackages.append(item.name)

    return subpackages


def classify_layers(subpackages: list[str]) -> tuple[list[str], list[list[str]]]:
    domain_keywords = {"domain", "model", "models", "entity", "entities", "core"}
    app_keywords = {"application", "app", "usecase", "usecases", "service", "services", "port", "ports", "interactor"}
    adapter_keywords = {"adapter", "adapters", "controller", "controllers", "gateway", "gateways", "presenter", "presenters", "repository", "repositories", "resource", "resources", "api", "dto", "endpoint", "endpoints"}
    infra_keywords = {"infrastructure", "infra", "persistence", "db", "database", "config", "configuration", "web", "server", "driver", "client"}

    level0 = []
    level1 = []
    level2 = []
    level3 = []

    for pkg in subpackages:
        lower = pkg.lower()
        if any(k in lower for k in domain_keywords):
            level0.append(pkg)
        elif any(k in lower for k in app_keywords):
            level1.append(pkg)
        elif any(k in lower for k in adapter_keywords):
            level2.append(pkg)
        elif any(k in lower for k in infra_keywords):
            level3.append(pkg)
        else:
            level1.append(pkg)

    if not level0:
        level0 = ["domain"]
    if not level1:
        level1 = ["application"]
    if not level2:
        level2 = ["adapters"]

    levels = [level0, level1, level2]
    if level3:
        levels.append(level3)

    order = []
    for group in levels:
        for p in group:
            if p not in order:
                order.append(p)

    return order, levels


def generate_policy(root: Path, title: str | None = None, prefix: str | None = None) -> dict:
    lang = detect_project_language(root)
    src_root = detect_source_root(root, lang)
    full_src = root / src_root

    detected_prefix = prefix or find_common_package_prefix(full_src)
    project_title = title or root.name.replace("-", " ").replace("_", " ").title()

    subpackages = discover_subpackages(full_src, detected_prefix)
    order, levels = classify_layers(subpackages)

    foreign_map = {
        "java": ["java.", "jakarta.", "org.springframework.", "io.quarkus."],
        "kotlin": ["kotlin.", "kotlinx."],
        "typescript": ["node:", "@types/"],
        "python": ["sys", "os", "pathlib"],
        "go": ["fmt", "os", "net/http"],
        "rust": ["std::", "core::"],
        "clojure": ["clojure."]
    }

    return {
        "title": project_title,
        "src": src_root,
        "prefix": detected_prefix,
        "hierarchical": True,
        "order": order,
        "levels": levels,
        "foreign": foreign_map.get(lang, ["java.", "jakarta."]),
        "omit": ["target", "build", ".git", "node_modules", ".gradle", ".idea", ".vscode"],
        "lang": lang
    }


def write_workbench_config(config_dir: Path, server_url: str = "http://localhost:8088") -> None:
    config_file = config_dir / "workbench.config.json"
    data = {
        "serverUrl": server_url,
        "protocolVersion": "1.0",
        "description": "Visual Workbench communication endpoint and mailbox IPC configuration"
    }
    config_file.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def update_companion_markdown(file_path: Path, server_url: str = "http://localhost:8088") -> None:
    companion_block = f"""
## Archlens Clean Architecture Workbench Companion Protocol

This project is governed by the **Archlens Dynamic Clean Architecture Workbench**.
- **Workbench UI & API**: `{server_url}`
- **Architectural Policy**: `.archlens/policy.json`
- **Mailbox IPC**:
  - Inbound queue: `.archlens/to-agent.json`
  - Outbound response: `.archlens/to-viewer.json`

### Handling Mailbox Commands:
1. **REGEN**: Re-index AST, evaluate package dependency rules, and acknowledge.
2. **APPLY_PROPOSAL**: Reorganize packages to conform to concentric rings (Adapters -> Application -> Domain Core). Where an inner layer relies on an outer layer, apply the **Dependency Inversion Principle** by creating an interface port in the inner layer and implementing it in the outer layer.
3. **REFRESH_CRAP**: Execute test suite and report code coverage metrics.
"""
    if not file_path.exists():
        file_path.write_text(f"# Assistant Guidelines\n{companion_block}\n", encoding="utf-8")
        return

    content = file_path.read_text(encoding="utf-8")
    modified = False
    if ".uml-viewer" in content:
        content = content.replace(".uml-viewer", ".archlens")
        modified = True

    if "Archlens Clean Architecture Workbench Companion Protocol" not in content:
        content = content.rstrip() + "\n\n" + companion_block.strip() + "\n"
        modified = True

    if modified:
        file_path.write_text(content, encoding="utf-8")


def upgrade_project(root: Path, server_url: str = "http://localhost:8088") -> int:
    legacy_dir = root / ".uml-viewer"
    primary_dir = root / ".archlens"

    if not legacy_dir.exists() and not primary_dir.exists():
        print(f"Error: No configuration found in {root}. Run without --upgrade to initialize.", file=sys.stderr)
        return 1

    if legacy_dir.exists():
        if not primary_dir.exists():
            legacy_dir.rename(primary_dir)
            print(f"Migrated legacy directory: {legacy_dir.name} -> {primary_dir.name}")
        else:
            for item in legacy_dir.iterdir():
                target = primary_dir / item.name
                if not target.exists():
                    item.rename(target)
            import shutil
            shutil.rmtree(legacy_dir, ignore_errors=True)
            print(f"Merged legacy files into: {primary_dir.name}")

    update_companion_markdown(root / "CLAUDE.md", server_url)
    update_companion_markdown(root / "AGENTS.md", server_url)
    print("Updated companion files (CLAUDE.md, AGENTS.md)")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Initialize or upgrade Archlens architectural policy (.archlens/policy.json) and agent guidelines."
    )
    parser.add_argument("--path", "-p", default=".", help="Target project root directory (default: current directory)")
    parser.add_argument("--title", "-t", default=None, help="Custom project title")
    parser.add_argument("--prefix", default=None, help="Custom common package prefix")
    parser.add_argument("--server-url", default="http://localhost:8088", help="Archlens workbench server URL")
    parser.add_argument("--upgrade", "-u", action="store_true", help="Upgrade legacy .uml-viewer configuration to .archlens")
    parser.add_argument("--force", "-f", action="store_true", help="Overwrite existing configuration files")

    args = parser.parse_args()
    target_root = Path(args.path).resolve()

    if not target_root.exists():
        print(f"Error: Target directory does not exist: {target_root}", file=sys.stderr)
        return 1

    if args.upgrade:
        return upgrade_project(target_root, server_url=args.server_url)

    archlens_dir = target_root / ".archlens"
    legacy_dir = target_root / ".uml-viewer"

    if not archlens_dir.exists() and legacy_dir.exists() and not args.force:
        print(f"Notice: Found legacy {legacy_dir.name} configuration. Run with --upgrade to migrate to .archlens.")
        target_config_dir = legacy_dir
    else:
        archlens_dir.mkdir(parents=True, exist_ok=True)
        target_config_dir = archlens_dir

    policy_file = target_config_dir / "policy.json"
    if policy_file.exists() and not args.force:
        print(f"Notice: Policy file already exists at {policy_file}. Use --force to regenerate.")
    else:
        policy = generate_policy(target_root, title=args.title, prefix=args.prefix)
        policy_file.write_text(json.dumps(policy, indent=2) + "\n", encoding="utf-8")
        print(f"Created architectural policy: {policy_file}")

    write_workbench_config(target_config_dir, server_url=args.server_url)
    print(f"Created workbench config: {target_config_dir / 'workbench.config.json'}")

    update_companion_markdown(target_root / "CLAUDE.md", server_url=args.server_url)
    update_companion_markdown(target_root / "AGENTS.md", server_url=args.server_url)
    print("Configured companion guidelines in CLAUDE.md and AGENTS.md")

    print(f"\nArchlens configuration complete for: {target_root.name}")
    print(f"Run Archlens or visit {args.server_url}?projectRoot={target_root} to inspect architecture.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
