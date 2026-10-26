# SPEC-02: TypeScript/JS Multi-Line ESM, Barrel Re-Exports, tsconfig Aliases & CLI Scanner

- **Linked Capability**: `CAP-02`
- **Status**: `ACTIVE`
- **Source Reference**: [Polyglot AST Scanners & Surgical DIP Inverter Problem Report — Issue 2](https://gist.github.com/djulen/2ddfd48bbc3792d42856d984d6afbb32)

## 1. Domain Contract & Acceptance Criteria

1. **Logical Statement Normalization & Full ESM Import Coverage (`TypeScriptAstScanner.java`)**:
   - Normalize physical lines into logical statements by stripping `/* ... */` multi-line block comments and `//` line comments outside string/template literals, and joining multi-line `import { ... } from '...'` and `export { ... } from '...'` clauses.
   - Support default imports (`import Foo from './foo'`), named imports (`import { A, B as C } from './mod'`), combined default + named imports (`import React, { useState } from 'react'`), namespace imports (`import * as ns from './mod'`), type imports (`import type { T } from './mod'`, `import type * as T from './mod'`), side-effect imports (`import './setup'`), `require('...')`, and dynamic `import('...')`.
2. **Accurate Stereotypes & Method Signature Extraction (`TypeScriptAstScanner.java`)**:
   - Assign `ClassNode.Stereotype.INTERFACE` to `interface` declarations and `ClassNode.Stereotype.ABSTRACT` to `abstract class` declarations.
   - Extract method signatures (`name`, parameter list `args`, `returnType`) from TypeScript classes and interfaces so `ArchitectureFitnessCalculator` computes accurate Abstractness ($A$) and `DipInversionSynthesizer` extracts non-empty Port contracts.
3. **Barrel (`index.ts` / `index.js`) Re-Exports, `tsconfig.json` Paths & Hybrid Class-First Edges (`TypeScriptAstScanner.java`)**:
   - Parse `tsconfig.json` / `jsconfig.json` `compilerOptions.paths` and SvelteKit `$lib/` aliases to resolve internal module paths.
   - Index `index.ts` / `index.tsx` / `index.js` barrel re-exports (`export { X } from './x'`, `export * from './x'`) across up to 4 hops.
   - Emit `DependencyEdge`s using Hybrid Class-First Resolution (`currentModule + "." + ClassName` to target class/interface ID when imported symbols match declared types, falling back to module IDs).
4. **Headless CLI TypeScript/JS Import Extraction (`cli/src/checker.js`)**:
   - Update `extractFileImports()` in `cli/src/checker.js` to properly parse single-line and multi-line ESM `import ... from '...'`, `export ... from '...'`, `require('...')`, and dynamic `import('...')` for `.ts`, `.tsx`, `.js`, `.jsx`, and `.svelte` files (resolving relative paths and aliases) instead of applying the Java `import a.b.c` regex.
