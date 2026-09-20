# Verification & Test Coverage Report: Dynamic UML Workbench

## 1. JaCoCo Coverage Configuration

JaCoCo plugin (`jacoco-maven-plugin:0.8.12`) has been configured in `backend/pom.xml` with:
- `prepare-agent`: Attaches coverage runtime agent during test execution.
- `report`: Generates HTML and XML coverage reports under `target/site/jacoco/`.
- `check`: Enforces **100% (1.00) coverage ratio** for both **LINE** and **BRANCH** counters at the bundle level.

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
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
                                <minimum>1.00</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>1.00</minimum>
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
1. [`DomainModelsTest.java`](file:///Users/fady/workspace/labs/unclebob-design/backend/src/test/java/com/design/umlviewer/domain/model/DomainModelsTest.java):
   - Exhaustively tests `CrapScore`, `MethodNode`, `FieldNode`, `ClassNode`, `ComponentNode`, `DependencyEdge`, and `ArchitectureGraph`.
   - Validates immutability, `withViolating(...)`, and all enum constants (`Stereotype`, `Kind`).
2. [`DependencyRuleValidatorFullTest.java`](file:///Users/fady/workspace/labs/unclebob-design/backend/src/test/java/com/design/umlviewer/domain/policy/DependencyRuleValidatorFullTest.java):
   - Tests `fromLevels` (including null level groups and unranked packages).
   - Tests `fromProposal` (virtual layers, exclusions).
   - Tests Clean Architecture violation triggers ($fromRank < toRank$), association/implements exemptions, and longest dotted prefix resolution.
3. [`CrapScoreCalculatorTest.java`](file:///Users/fady/workspace/labs/unclebob-design/backend/src/test/java/com/design/umlviewer/metrics/CrapScoreCalculatorTest.java):
   - Tests CRAP formula bounds: 0% coverage, 100% coverage, out-of-bounds clamps, single methods, and population $\sigma$ aggregation.
4. [`FileMailboxServiceTest.java`](file:///Users/fady/workspace/labs/unclebob-design/backend/src/test/java/com/design/umlviewer/domain/mailbox/FileMailboxServiceTest.java):
   - Tests initial state on empty directories.
   - Tests atomic append of commands to `.uml-viewer/to-agent.json` and `.uml-viewer/to-viewer.json`.
   - Tests FIFO pop operations and empty-queue handling.
5. [`JavaAstScannerTest.java`](file:///Users/fady/workspace/labs/unclebob-design/backend/src/test/java/com/design/umlviewer/scanner/JavaAstScannerTest.java):
   - Uses JavaParser to scan interfaces, classes, records, methods, fields, and internal dependencies.
   - Validates AST extraction and Cyclomatic Complexity calculations.
6. [`GraphCompilerTest.java`](file:///Users/fady/workspace/labs/unclebob-design/backend/src/test/java/com/design/umlviewer/engine/GraphCompilerTest.java):
   - Validates loading default and custom `policy.json`.
   - Validates compilation of real namespace graphs vs virtual proposal layers.
7. [`DiagramResourceTest.java`](file:///Users/fady/workspace/labs/unclebob-design/backend/src/test/java/com/design/umlviewer/resource/DiagramResourceTest.java):
   - Tests `/api/graph`, `/api/policy`, `/api/mailbox/to-agent`, and source code jump resolution (`/api/source`).

### Frontend (Svelte 5 / Vitest)
1. [`colors.test.ts`](file:///Users/fady/workspace/labs/unclebob-design/frontend/src/lib/utils/colors.test.ts): Tests CRAP + mutation health score interpolation and formatting.
2. [`diagram.test.ts`](file:///Users/fady/workspace/labs/unclebob-design/frontend/src/lib/state/diagram.test.ts): Tests Svelte 5 Runes state, zoom/pan reset, and declutter mode cycle.
