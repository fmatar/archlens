# SPEC-01: Python AST Class-Level Edges, ABC/Protocol Stereotypes & Method Signatures

- **Linked Capability**: `CAP-01`
- **Status**: `ACTIVE`
- **Source Reference**: [Polyglot AST Scanners & Surgical DIP Inverter Problem Report — Issue 1](https://gist.github.com/djulen/2ddfd48bbc3792d42856d984d6afbb32)

## 1. Domain Contract & Acceptance Criteria

1. **Class-Level & Symbol-Aware Dependency Edges (`PythonAstScanner.java`)**:
   - Index all discovered Python classes (`moduleId + "." + ClassName`) and `__init__.py` barrel re-exports in Pass 1 / Pass 1.5 so `from pkg.mod import ClassName` resolves directly to `pkg.mod.ClassName` when `ClassName` is a known class in `pkg.mod` (or re-exported via `__init__.py`).
   - When a source `.py` file declares one or more classes, emit outgoing `DependencyEdge` instances from each declaring class (`currentModule + "." + clsName`) to the resolved target class ID (or target module's classes / fallback module `ClassNode` ID if the target module is function-only).
   - When a source `.py` file is function-only (no `class` declarations), emit edges from `currentModule`.
2. **Python Abstract & Protocol Stereotypes (`PythonAstScanner.java`)**:
   - Detect classes inheriting from `Protocol`, `typing.Protocol`, `typing_extensions.Protocol` as `ClassNode.Stereotype.INTERFACE`.
   - Detect classes inheriting from `ABC`, `abc.ABC`, `ABCMeta`, or containing `@abstractmethod` / `@abc.abstractmethod` methods as `ClassNode.Stereotype.ABSTRACT` (or `INTERFACE` when all public methods are abstract).
3. **Class-Scoped Method Signatures & Type Annotations (`PythonAstScanner.java`)**:
   - Scope `def` methods to their enclosing `class` declaration (based on indentation level) rather than sharing a single file-global `methods` list across all classes in the file.
   - Extract parameter names and optional type annotations (excluding `self` and `cls`) into `MethodNode.args()` and return type annotations (`-> ReturnType:`) into `MethodNode.returnType()`.
4. **Module-Prefix Fallback in `ArchitectureFitnessCalculator.java`**:
   - Ensure `ArchitectureFitnessCalculator.calculateMaxMainSequenceDistance()` indexes module prefixes (`cls.id().substring(0, cls.id().length() - cls.name().length() - 1)`) in `compClassKeys` alongside `cls.id()`, `cls.name()`, and `cls.packageName() + "." + cls.name()`.
