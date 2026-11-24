# Contributing to Archlens

Thank you for your interest in contributing to **Archlens**! We welcome contributions of all kinds: polyglot AST scanners, visualization enhancements, UI/UX polish, Model Context Protocol (MCP) tooling, bug fixes, documentation improvements, and autonomous agent workflows.

> [!TIP]
> For an in-depth architectural breakdown, instructions on adding new polyglot language scanners, MCP server extension guides, and test engineering standards, see the **[Developer & Contributor Guide](docs/guide/DEVELOPER_GUIDE.md)** and **[Contributors](CONTRIBUTORS.md)**.

---

## Code of Conduct

Archlens has adopted the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating in this project, you agree to abide by its terms.

---

## Development Setup

### Prerequisites

- **Java**: Java 25 (OpenJDK / GraalVM 25)
- **Maven**: Apache Maven 3.9+
- **Node.js**: Node.js 22+ and `pnpm` (for frontend) / `npm` (for CLI)
- **Docker**: (Optional) Docker Engine 24+ for container packaging

### Repository Layout

```text
archlens/
├── backend/            # Quarkus 3.x REST, SSE & native MCP server, Polyglot AST Scanners, Rule Engine
├── frontend/           # Svelte 5 + Tailwind CSS v4 dynamic visualization canvas
├── cli/                # Zero-dependency CLI (@fadym/archlens-skill), MCP registry, stdio runner
├── docs/               # Architecture specs (C4), User Guide, Developer Guide, Vision
├── .archlens/          # Active architectural policy and mailbox IPC queue
└── pom.xml             # Root reactor POM (com.slixes:archlens)
```

---

## Local Development Workflow

### 1. Running the Backend (Quarkus Dev Mode)

The backend provides hot-reload during development:

```bash
mvn -f backend/pom.xml quarkus:dev
```
The REST API, SSE stream, and MCP endpoints start at `http://127.0.0.1:8088` (share `ARCHLENS_TOKEN` with the frontend dev server, or set `ARCHLENS_AUTH_ENABLED=false` for local-only hacking).

### 2. Running the Frontend (Vite Dev Server)

```bash
cd frontend
pnpm install
pnpm dev
```
Open **`http://localhost:5173`** in your browser. The Vite development server proxies API and MCP requests to port `8088`.

### 3. Running the CLI (`@fadym/archlens-skill`)

```bash
cd cli
npm test
node bin/index.js --help
```

### 4. Running Containerized

To test the single-container distribution locally:

```bash
docker run -d -p 127.0.0.1:8088:8088 -v "$(pwd):/workspace:ro" ghcr.io/fmatar/archlens:latest
```

---

## Quality Standards & Formatting

Before opening a pull request, ensure all linters, formatting, and test gates pass:

```bash
# Apply Spotless code formatting to Java sources
mvn -f backend/pom.xml spotless:apply

# Run backend build, unit tests, JaCoCo, PMD, and SpotBugs gates
mvn -f backend/pom.xml clean spotless:check verify pmd:check spotbugs:check

# Run CLI & repository script test suites
npm test

# Run frontend unit tests and type checks
pnpm --dir frontend run check
pnpm --dir frontend test
```

---

## Branching & Pull Request Process

1. **Branch off `develop`**:
   All feature and fix branches must branch off and target the `develop` integration branch:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/my-feature
   ```
2. **Commit Conventions**:
   We follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` A new feature, scanner, or MCP tool
   - `fix:` A bug fix
   - `docs:` Documentation updates
   - `chore:` Build scripts, dependencies, or repository hygiene
   - `refactor:` Code restructuring without behavioral changes
   - `test:` Adding or updating tests
3. **Open a Pull Request**:
   - Push your branch to GitHub and open a PR against `develop`.
   - Complete all items in the pull request checklist.
   - Once reviewed and CI status checks pass, the PR will be merged into `develop` and promoted to `main` on release.
