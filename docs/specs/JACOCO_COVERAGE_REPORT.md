# Verification & Test Coverage Report: Archlens Dynamic Clean Architecture Workbench

## 1. JaCoCo Coverage Configuration

JaCoCo (`jacoco-maven-plugin:0.8.16`) is configured in [`backend/pom.xml`](../../backend/pom.xml) with:
- `prepare-agent`: Attaches the coverage runtime agent during JUnit 5 test execution.
- `report`: Generates HTML and XML coverage reports under `backend/target/site/jacoco/` during `test`.
- `check`: Enforces **`>= 75%` (`0.75`) LINE** and **`>= 55%` (`0.55`) BRANCH** coverage ratios at the Maven bundle level, complemented by the SDLC diff-coverage gate (**`>= 85%` LINE and BRANCH** on modified lines).

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.55</minimum>
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
1. [`DependencyRuleValidatorFullTest.java`](../../backend/src/test/java/io/slixes/archlens/domain/policy/DependencyRuleValidatorFullTest.java) & [`PackageCycleDetectorTest.java`](../../backend/src/test/java/io/slixes/archlens/domain/policy/PackageCycleDetectorTest.java):
   - Tests `fromLevels`, `fromProposal`, Clean Architecture violation triggers ($fromRank < toRank$), and Tarjan's Strongly Connected Components (SCC) cycle detection.
2. [`CrapScoreCalculatorTest.java`](../../backend/src/test/java/io/slixes/archlens/metrics/CrapScoreCalculatorTest.java):
   - Tests CRAP formula bounds: 0% coverage, 100% coverage, out-of-bounds clamps, single methods, and population $\sigma$ aggregation.
3. [`FileMailboxServiceTest.java`](../../backend/src/test/java/io/slixes/archlens/mailbox/fs/FileMailboxServiceTest.java):
   - Tests initial state on unopened directories, atomic append of commands to `.archlens/to-agent.json` and `.archlens/to-viewer.json`, and FIFO pop operations.
4. [`JavaAstScannerTest.java`](../../backend/src/test/java/io/slixes/archlens/scanner/JavaAstScannerTest.java), [`PythonLexicalScannerTest.java`](../../backend/src/test/java/io/slixes/archlens/scanner/PythonLexicalScannerTest.java), [`TypeScriptLexicalScannerTest.java`](../../backend/src/test/java/io/slixes/archlens/scanner/TypeScriptLexicalScannerTest.java), [`GoLexicalScannerTest.java`](../../backend/src/test/java/io/slixes/archlens/scanner/GoLexicalScannerTest.java), [`RustLexicalScannerTest.java`](../../backend/src/test/java/io/slixes/archlens/scanner/RustLexicalScannerTest.java), & [`FileScannerUtilTest.java`](../../backend/src/test/java/io/slixes/archlens/scanner/FileScannerUtilTest.java):
   - Validates polyglot scanning, stereotypes, method signatures, internal dependencies, cross-language route linking, and `skippedFiles` telemetry across all 5 supported languages.
5. [`GraphCompilerTest.java`](../../backend/src/test/java/io/slixes/archlens/engine/GraphCompilerTest.java):
   - Validates loading default and custom `.archlens/policy.json`, propagating `skippedFiles`, and compiling real package graphs vs virtual proposal layers.
6. [`DiagramResourceTest.java`](../../backend/src/test/java/io/slixes/archlens/delivery/rest/DiagramResourceTest.java), [`AuthFilterTest.java`](../../backend/src/test/java/io/slixes/archlens/delivery/rest/AuthFilterTest.java), & [`ArchlensMcpServiceTest.java`](../../backend/src/test/java/io/slixes/archlens/mcp/ArchlensMcpServiceTest.java):
   - Tests loopback auth, path confinement, REST endpoints (`/api/graph`, `/api/policy`, `/api/mailbox/*`, `/api/source`), and all 11 MCP tools, 3 resources, and 2 prompts.

### Frontend (Svelte 5 / Vitest)
1. [`colors.test.ts`](../../frontend/src/lib/utils/colors.test.ts) & [`martinMetrics.test.ts`](../../frontend/src/lib/utils/martinMetrics.test.ts): Tests CRAP + mutation health score interpolation and component coupling metrics ($C_a, C_e, I, A, D$).
2. [`diagram.test.ts`](../../frontend/src/lib/state/diagram.test.ts): Tests Svelte 5 Runes state, sandbox class-move simulation, `skippedFilesCount`, zoom/pan reset, and declutter mode cycle.

### CLI & Repository Governance (Node.js Test Runner)
1. [`mcp-server.test.js`](../../cli/test/mcp-server.test.js), [`mcp-registry.test.js`](../../cli/test/mcp-registry.test.js), [`docker-runner.test.js`](../../cli/test/docker-runner.test.js), & [`checker.test.js`](../../cli/test/checker.test.js): Tests the 11-tool MCP stdio server, multi-agent manifest installer, `spawnSync` runner, and static CI checker.
2. [`sync-skills.test.js`](../../scripts/test/sync-skills.test.js) & [`ci-security.test.js`](../../scripts/test/ci-security.test.js): Tests skill parity, release versioning, and CI/workflow security invariants.
