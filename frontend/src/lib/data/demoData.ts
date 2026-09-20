import type { ArchitectureGraph, ArchitecturePolicy } from '../types/diagram';

export const DEMO_POLICY: ArchitecturePolicy = {
  title: "Clean Architecture Workbench",
  src: "src/main/java",
  prefix: "com.design.umlviewer",
  hierarchical: true,
  order: ["domain", "scanner", "metrics", "engine", "resource"],
  levels: [
    ["domain"],
    ["scanner", "metrics", "engine"],
    ["resource"]
  ],
  foreign: [],
  omit: [],
  proposals: [
    {
      "id": "clean-core",
      "name": "Decoupled Clean Core",
      "layers": [
        { "id": "domain", "label": "Domain Core", "packages": ["domain"] },
        { "id": "application", "label": "Application Services", "packages": ["scanner", "metrics", "engine"] },
        { "id": "adapters", "label": "Infrastructure & Adapters", "packages": ["resource"] }
      ],
      "omit": []
    },
    {
      "id": "flat-view",
      "name": "Single Package Architecture",
      "layers": [
        { "id": "monolith", "label": "All Components", "packages": ["domain", "engine", "resource"] }
      ],
      "omit": []
    }
  ]
};

export const DEMO_GRAPH_REAL: ArchitectureGraph = {
  title: "Clean Architecture Workbench",
  isProposal: false,
  activeProposalId: undefined,
  unassigned: [],
  components: [
    {
      id: "domain",
      label: "domain (Core)",
      level: 0,
      crap: { mu: 1.2, max: 2.0, sigma: 0.4 },
      mutationScore: 0.96,
      childPackageIds: ["com.design.umlviewer.domain"],
      classes: [
        {
          id: "com.design.umlviewer.domain.model.ClassNode",
          name: "ClassNode",
          packageName: "com.design.umlviewer.domain.model",
          filePath: "backend/src/main/java/com/design/umlviewer/domain/model/ClassNode.java",
          stereotype: "RECORD",
          isForeign: false,
          level: 0,
          crap: { mu: 1.0, max: 1.0, sigma: 0.0 },
          coverage: 1.0,
          cc: 1,
          killed: 4,
          survived: 0,
          uncovered: 0,
          fields: [
            { name: "id", type: "String", isPrivate: true },
            { name: "name", type: "String", isPrivate: true },
            { name: "stereotype", type: "Stereotype", isPrivate: true }
          ],
          methods: [
            {
              name: "stereotype",
              args: [],
              returnType: "Stereotype",
              isPrivate: false,
              cc: 1,
              coverage: 1.0,
              crap: 1.0,
              killed: 2,
              survived: 0,
              uncovered: 0,
              line: 8
            }
          ]
        },
        {
          id: "com.design.umlviewer.domain.policy.DependencyRuleValidator",
          name: "DependencyRuleValidator",
          packageName: "com.design.umlviewer.domain.policy",
          filePath: "backend/src/main/java/com/design/umlviewer/domain/policy/DependencyRuleValidator.java",
          stereotype: "CLASS",
          isForeign: false,
          level: 0,
          crap: { mu: 2.1, max: 3.5, sigma: 0.6 },
          coverage: 1.0,
          cc: 7,
          killed: 8,
          survived: 0,
          uncovered: 0,
          fields: [
            { name: "ranks", type: "Map<String, Integer>", isPrivate: true }
          ],
          methods: [
            {
              name: "evaluate",
              args: ["DependencyEdge edge"],
              returnType: "DependencyEdge",
              isPrivate: false,
              cc: 3,
              coverage: 1.0,
              crap: 3.0,
              killed: 4,
              survived: 0,
              uncovered: 0,
              line: 60
            },
            {
              name: "resolveRank",
              args: ["String id"],
              returnType: "Integer",
              isPrivate: false,
              cc: 4,
              coverage: 1.0,
              crap: 4.0,
              killed: 4,
              survived: 0,
              uncovered: 0,
              line: 47
            }
          ]
        }
      ]
    },
    {
      id: "engine",
      label: "engine & scanner",
      level: 1,
      crap: { mu: 3.2, max: 5.0, sigma: 0.9 },
      mutationScore: 0.92,
      childPackageIds: ["com.design.umlviewer.engine"],
      classes: [
        {
          id: "com.design.umlviewer.engine.GraphCompiler",
          name: "GraphCompiler",
          packageName: "com.design.umlviewer.engine",
          filePath: "backend/src/main/java/com/design/umlviewer/engine/GraphCompiler.java",
          stereotype: "CLASS",
          isForeign: false,
          level: 1,
          crap: { mu: 3.5, max: 5.0, sigma: 0.8 },
          coverage: 0.95,
          cc: 8,
          killed: 7,
          survived: 1,
          uncovered: 0,
          fields: [
            { name: "scanner", type: "JavaAstScanner", isPrivate: true }
          ],
          methods: [
            {
              name: "compileGraph",
              args: ["String root", "String proposalId"],
              returnType: "ArchitectureGraph",
              isPrivate: false,
              cc: 5,
              coverage: 0.95,
              crap: 5.0,
              killed: 5,
              survived: 1,
              uncovered: 0,
              line: 47
            }
          ]
        },
        {
          id: "com.design.umlviewer.scanner.JavaAstScanner",
          name: "JavaAstScanner",
          packageName: "com.design.umlviewer.scanner",
          filePath: "backend/src/main/java/com/design/umlviewer/scanner/JavaAstScanner.java",
          stereotype: "CLASS",
          isForeign: false,
          level: 1,
          crap: { mu: 4.1, max: 6.0, sigma: 1.1 },
          coverage: 0.9,
          cc: 12,
          killed: 9,
          survived: 2,
          uncovered: 0,
          fields: [
            { name: "javaParser", type: "JavaParser", isPrivate: true }
          ],
          methods: [
            {
              name: "scanProject",
              args: ["String root", "String src", "String prefix"],
              returnType: "ScanResult",
              isPrivate: false,
              cc: 8,
              coverage: 0.9,
              crap: 8.0,
              killed: 6,
              survived: 2,
              uncovered: 0,
              line: 42
            }
          ]
        }
      ]
    },
    {
      id: "resource",
      label: "resource (API & Web)",
      level: 2,
      crap: { mu: 2.0, max: 3.0, sigma: 0.5 },
      mutationScore: 0.95,
      childPackageIds: ["com.design.umlviewer.resource"],
      classes: [
        {
          id: "com.design.umlviewer.resource.DiagramResource",
          name: "DiagramResource",
          packageName: "com.design.umlviewer.resource",
          filePath: "backend/src/main/java/com/design/umlviewer/resource/DiagramResource.java",
          stereotype: "CLASS",
          isForeign: false,
          level: 2,
          crap: { mu: 2.0, max: 3.0, sigma: 0.5 },
          coverage: 1.0,
          cc: 6,
          killed: 6,
          survived: 0,
          uncovered: 0,
          fields: [
            { name: "graphCompiler", type: "GraphCompiler", isPrivate: true }
          ],
          methods: [
            {
              name: "getGraph",
              args: ["String projectRoot", "String proposalId"],
              returnType: "ArchitectureGraph",
              isPrivate: false,
              cc: 2,
              coverage: 1.0,
              crap: 2.0,
              killed: 3,
              survived: 0,
              uncovered: 0,
              line: 32
            },
            {
              name: "streamEvents",
              args: [],
              returnType: "Multi<Map<String, Object>>",
              isPrivate: false,
              cc: 1,
              coverage: 1.0,
              crap: 1.0,
              killed: 2,
              survived: 0,
              uncovered: 0,
              line: 78
            }
          ]
        }
      ]
    }
  ],
  edges: [
    {
      from: "engine",
      to: "domain",
      kind: "DEPENDENCY",
      label: "uses model & validator",
      isViolating: false
    },
    {
      from: "resource",
      to: "engine",
      kind: "DEPENDENCY",
      label: "compiles graph",
      isViolating: false
    }
  ]
};

export const DEMO_GRAPH_PROPOSAL: ArchitectureGraph = {
  ...DEMO_GRAPH_REAL,
  isProposal: true,
  activeProposalId: "clean-core",
  components: [
    {
      ...DEMO_GRAPH_REAL.components[0],
      label: "Domain Core (Pure)",
      level: 0
    },
    {
      ...DEMO_GRAPH_REAL.components[1],
      label: "Application Services",
      level: 1
    },
    {
      ...DEMO_GRAPH_REAL.components[2],
      label: "Infrastructure & Adapters",
      level: 2
    }
  ],
  edges: [
    {
      from: "engine",
      to: "domain",
      kind: "DEPENDENCY",
      label: "inward: ok",
      isViolating: false
    },
    {
      from: "resource",
      to: "engine",
      kind: "DEPENDENCY",
      label: "inward: ok",
      isViolating: false
    },
    {
      from: "domain",
      to: "resource",
      kind: "DEPENDENCY",
      label: "Clean Architecture Violation!",
      isViolating: true
    }
  ]
};
