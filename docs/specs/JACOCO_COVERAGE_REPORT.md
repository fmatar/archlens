# Verification & Test Coverage Report: Archlens Dynamic Clean Architecture Workbench

## 1. JaCoCo Coverage Configuration

JaCoCo plugin (`jacoco-maven-plugin:0.8.15`) is configured in [`backend/pom.xml`](file:///Users/fady/workspace/labs/archlens/backend/pom.xml) with:
- `prepare-agent`: Attaches coverage runtime agent during test execution.
- `report`: Generates HTML and XML coverage reports under `backend/target/site/jacoco/`.
- `check`: Enforces **$\ge 85\%$ (`0.85`) LINE** and **$\ge 75\%$ (`0.75`) BRANCH** coverage ratios at the bundle level.

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.version}</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.85</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 2. Test Suites Implemented

### Backend (Java 25 & JUnit 5)
1. [`DomainModelsTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/domain/graph/DomainModelsTest.java):
   - Exhaustively tests `CrapScore`, `MethodNode`, `FieldNode`, `ClassNode`, `ComponentNode`, `DependencyEdge`, and `ArchitectureGraph`.
   - Validates immutability, `withViolating(...)`, and all enum constants (`Stereotype`, `Kind`).
2. [`DependencyRuleValidatorFullTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/domain/policy/DependencyRuleValidatorFullTest.java) & [`PackageCycleDetectorTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/domain/policy/PackageCycleDetectorTest.java):
   - Tests `fromLevels`, `fromProposal`, Clean Architecture violation triggers ($fromRank < toRank$), and Tarjan's Strongly Connected Components (SCC) cycle detection.
3. [`CrapScoreCalculatorTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/metrics/CrapScoreCalculatorTest.java):
   - Tests CRAP formula bounds: 0% coverage, 100% coverage, out-of-bounds clamps, single methods, and population $\sigma$ aggregation.
4. [`FileMailboxServiceTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/mailbox/fs/FileMailboxServiceTest.java):
   - Tests initial state on empty directories, atomic append of commands to `.archlens/to-agent.json` and `.archlens/to-viewer.json`, and FIFO pop operations.
5. [`JavaAstScannerTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/scanner/JavaAstScannerTest.java), [`PythonAstScannerTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/scanner/PythonAstScannerTest.java), [`TypeScriptAstScannerTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/scanner/TypeScriptAstScannerTest.java), [`GoAstScannerTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/scanner/GoAstScannerTest.java), [`RustAstScannerTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/scanner/RustAstScannerTest.java), [`ClojureAstScannerTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/scanner/ClojureAstScannerTest.java):
   - Validates polyglot AST extraction, stereotypes, method signatures, and internal dependencies across all 6 supported languages.
6. [`GraphCompilerTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/engine/GraphCompilerTest.java):
   - Validates loading default and custom `.archlens/policy.json` and compiling real namespace graphs vs virtual proposal layers.
7. [`DiagramResourceTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/delivery/rest/DiagramResourceTest.java) & [`ArchlensMcpServiceTest.java`](file:///Users/fady/workspace/labs/archlens/backend/src/test/java/io/slixes/archlens/mcp/ArchlensMcpServiceTest.java):
   - Tests REST endpoints (`/api/graph`, `/api/policy`, `/api/mailbox/*`, `/api/source`) and all 10 MCP tools, 3 resources, and 2 prompts.

### Frontend (Svelte 5 / Vitest)
1. [`colors.test.ts`](file:///Users/fady/workspace/labs/archlens/frontend/src/lib/utils/colors.test.ts) & [`martinMetrics.test.ts`](file:///Users/fady/workspace/labs/archlens/frontend/src/lib/utils/martinMetrics.test.ts): Tests CRAP + mutation health score interpolation and Robert C. Martin metrics ($C_a, C_e, I, A, D$).
2. [`diagram.test.ts`](file:///Users/fady/workspace/labs/archlens/frontend/src/lib/state/diagram.test.ts): Tests Svelte 5 Runes state, sandbox class-move simulation, zoom/pan reset, and declutter mode cycle.

### CLI & SDLC Engine (Node.js Test Runner)
1. [`mcp-server.test.js`](file:///Users/fady/workspace/labs/archlens/cli/test/mcp-server.test.js), [`mcp-registry.test.js`](file:///Users/fady/workspace/labs/archlens/cli/test/mcp-registry.test.js), & [`checker.test.js`](file:///Users/fady/workspace/labs/archlens/cli/test/checker.test.js): Tests the 10-tool MCP stdio server, multi-agent manifest installer, and static CI checker.
2. [`sdlc-workflow.test.mjs`](file:///Users/fady/workspace/labs/archlens/tests/sdlc-workflow.test.mjs): Tests the SDLC engine, ADR drift sentinel, and AST-to-Wiki synchronization (`sdlc-wiki`).
