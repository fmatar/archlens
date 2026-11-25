---
id: ADR-022
title: "Wave 3 — Honest Lexical Scanner Naming & Comment/String Literal Pre-Pass Sanitization"
scope: architectural
status: accepted
ticket: 243
supersedes: [ADR-021]
createdAt: 2026-11-25T05:30:11.135Z
decidedBy: "@fmatar"
decidedAt: 2026-11-25T06:08:16.288Z
decisionReason: "Approved Path A under Wave 3 (#243): pure-JVM *LexicalScanner renames and comment/string sanitization."
---

# ADR-022: Wave 3 — Honest Lexical Scanner Naming & Comment/String Literal Pre-Pass Sanitization

- **Scope**: `architectural`
- **Status**: `accepted`
- **Linked Ticket**: `243`
- **Supersedes / Modifies**: `ADR-021`

## 1. Context & Spec Drift Trigger
Issue #243 identified that non-Java scanners (Python, TypeScript/JS/Svelte, Go, Rust) were named *AstScanner despite performing lexical statement normalization rather than full syntax-tree parsing, and could emit false-positive DependencyEdges when import/use/require patterns appeared inside multiline block comments, raw strings, template literals, or quoted string literals.

## 2. Decision
Adopt Path A (100% pure-JVM, preserving zero-JNI GraalVM native builds): rename PythonAstScanner, TypeScriptAstScanner, GoAstScanner, and RustAstScanner to *LexicalScanner (retaining JavaAstScanner for JavaParser 3.28 AST), add line-preserving comment/raw-string/string-literal sanitizers in FileScannerUtil and TypeScriptLexicalScanner, and align README.md, PRODUCT.md, specs, and wiki.

## 3. Consequences & Reversal Criteria
Commented, raw-string, multiline template, and quoted import patterns produce zero false violations across Python, TypeScript, Go, and Rust; scanner class names and documentation accurately distinguish Java AST parsing from lexical statement scanning without introducing native JNI runtime dependencies.
