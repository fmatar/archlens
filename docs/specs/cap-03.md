# SPEC-03: Polyglot Surgical DIP Inverter & CLI Main Sequence Distance

- **Linked Capability**: `CAP-03`
- **Status**: `ACTIVE`
- **Depends On**: `CAP-01`, `CAP-02`
- **Source Reference**: [Polyglot AST Scanners & Surgical DIP Inverter Problem Report — Issue 3](https://gist.github.com/djulen/2ddfd48bbc3792d42856d984d6afbb32)

## 1. Domain Contract & Acceptance Criteria

1. **Polyglot Code & Path Synthesis (`DipInversionSynthesizer.java`)**:
   - Detect project language from `policy.lang()` or `fromNode.filePath()` / `toNode.filePath()` extension (`.py`, `.ts`/`.tsx`/`.js`/`.svelte`, `.go`, `.rs`, defaulting to `.java`).
   - **Python (`python`)**:
     - Port path: `<src_dir>/<caller_package_path>/ports/<snake_case_port>.py`.
     - Port code: `from typing import Protocol, Any` + `class <PortName>(Protocol):` with extracted `def <method>(self, <args>) -> <returnType>: ...` signatures.
     - Adapter preview: `class <Adapter>(<PortName>):` importing `<PortName>` from `<portPackage>`.
     - Caller preview: `class <Caller>:` with `def __init__(self, <field_name>: <PortName>) -> None:` constructor injection.
     - Surgical prompt: `python` fenced code blocks and `pytest` verification instructions.
   - **TypeScript / JavaScript (`typescript` / `javascript`)**:
     - Port path: `<src_dir>/<caller_package_path>/ports/<PortName>.ts`.
     - Port code: `export interface <PortName> { <method>(<args>): <returnType>; }`.
     - Adapter preview: `export class <Adapter> implements <PortName>`.
     - Caller preview: `export class <Caller> { constructor(private readonly <field>: <PortName>) {} }`.
     - Surgical prompt: `typescript` fenced code blocks and `npm test` verification instructions.
   - **Go (`go`) & Rust (`rust`)**:
     - Provide idiomatic `type <PortName> interface` (`.go`, `go test ./...`) and `pub trait <PortName>` (`.rs`, `cargo test`) templates.
2. **Module-to-Class Lookup & Dynamic Filename Display**:
   - Ensure `DipInversionSynthesizer.findClassNode()` and `ArchitecturalDossierGenerator.indexClasses()` match module prefixes across both assigned `components()` and `unassigned()` nodes.
   - Update `InvertDipModal.svelte` to display the actual synthesized filename (`plan.portFilePath.split('/').pop()`) instead of hardcoding `{plan.portName}.java`.
3. **Real Main Sequence Distance ($D = |A + I - 1|$) in Headless CLI (`cli/src/checker.js`)**:
   - Replace the hardcoded `maxDistance: 0.12` in `checkArchitecture()` (`cli/src/checker.js`) with a real calculation of component Abstractness ($A$), Instability ($I = C_e / (C_a + C_e)$), and Maximum Main Sequence Distance ($D = |A + I - 1|$) across scanned source files.
