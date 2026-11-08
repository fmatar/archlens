import type { ArchitectureGraph, ArchitecturePolicy } from '../types/diagram';

export const DEMO_POLICY: ArchitecturePolicy = {
  title: "Clean Architecture Workbench",
  src: "src/main/java",
  prefix: "io.slixes.archlens",
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
      childPackageIds: ["io.slixes.archlens.domain"],
      classes: [
        {
          id: "io.slixes.archlens.domain.model.ClassNode",
          name: "ClassNode",
          packageName: "io.slixes.archlens.domain.model",
          filePath: "backend/src/main/java/io/slixes/archlens/domain/model/ClassNode.java",
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
          id: "io.slixes.archlens.domain.policy.DependencyRuleValidator",
          name: "DependencyRuleValidator",
          packageName: "io.slixes.archlens.domain.policy",
          filePath: "backend/src/main/java/io/slixes/archlens/domain/policy/DependencyRuleValidator.java",
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
      childPackageIds: ["io.slixes.archlens.engine"],
      classes: [
        {
          id: "io.slixes.archlens.engine.GraphCompiler",
          name: "GraphCompiler",
          packageName: "io.slixes.archlens.engine",
          filePath: "backend/src/main/java/io/slixes/archlens/engine/GraphCompiler.java",
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
          id: "io.slixes.archlens.scanner.JavaAstScanner",
          name: "JavaAstScanner",
          packageName: "io.slixes.archlens.scanner",
          filePath: "backend/src/main/java/io/slixes/archlens/scanner/JavaAstScanner.java",
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
      childPackageIds: ["io.slixes.archlens.resource"],
      classes: [
        {
          id: "io.slixes.archlens.resource.DiagramResource",
          name: "DiagramResource",
          packageName: "io.slixes.archlens.resource",
          filePath: "backend/src/main/java/io/slixes/archlens/resource/DiagramResource.java",
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

export interface ShowcasePreset {
  id: string;
  name: string;
  subtitle: string;
  language: string;
  policy: ArchitecturePolicy;
  graph: ArchitectureGraph;
}

export const SHOWCASE_PRESETS: ShowcasePreset[] = [
  {
    id: 'spring-petclinic',
    name: 'Spring PetClinic (Java 25)',
    subtitle: 'Veterinary domain model with an outward JPA persistence violation ready for DIP inversion',
    language: 'java',
    policy: {
      title: 'Spring PetClinic Clean Architecture',
      src: 'src/main/java',
      prefix: 'org.springframework.samples.petclinic',
      hierarchical: true,
      order: ['domain', 'application', 'adapters', 'infrastructure'],
      levels: [['domain'], ['application'], ['adapters'], ['infrastructure']],
      foreign: ['java.', 'jakarta.'],
      omit: ['target'],
      lang: 'java',
      proposals: []
    },
    graph: {
      title: 'Spring PetClinic Clean Architecture',
      isProposal: false,
      unassigned: [],
      screamingMetric: {
        score: 0.85,
        domainPackageCount: 3,
        technicalPackageCount: 1,
        totalPackageCount: 4,
        classification: 'PACKAGE_BY_FEATURE',
        domainPackages: ['domain.vet', 'domain.pet', 'application.clinic'],
        technicalPackages: ['infrastructure.jpa'],
        frameworkGravityWarnings: []
      },
      cycles: [],
      components: [
        {
          id: 'domain',
          label: 'domain.vet (Ring 0)',
          level: 0,
          crap: { mu: 1.4, max: 2.2, sigma: 0.3 },
          mutationScore: 0.94,
          childPackageIds: ['org.springframework.samples.petclinic.domain'],
          classes: [
            {
              id: 'org.springframework.samples.petclinic.domain.Vet',
              name: 'Vet',
              packageName: 'org.springframework.samples.petclinic.domain',
              filePath: 'src/main/java/org/springframework/samples/petclinic/domain/Vet.java',
              stereotype: 'RECORD',
              isForeign: false,
              level: 0,
              crap: { mu: 1.0, max: 1.0, sigma: 0.0 },
              coverage: 1.0,
              cc: 1,
              killed: 4,
              survived: 0,
              uncovered: 0,
              fields: [{ name: 'id', type: 'UUID', isPrivate: true }],
              methods: []
            },
            {
              id: 'org.springframework.samples.petclinic.domain.VetSpecialtyValidator',
              name: 'VetSpecialtyValidator',
              packageName: 'org.springframework.samples.petclinic.domain',
              filePath: 'src/main/java/org/springframework/samples/petclinic/domain/VetSpecialtyValidator.java',
              stereotype: 'CLASS',
              isForeign: false,
              level: 0,
              crap: { mu: 2.1, max: 3.0, sigma: 0.4 },
              coverage: 0.92,
              cc: 3,
              killed: 6,
              survived: 0,
              uncovered: 0,
              fields: [{ name: 'jpaRepo', type: 'JpaVetRepository', isPrivate: true }],
              methods: [
                {
                  name: 'validateSpecialty',
                  args: ['Vet vet'],
                  returnType: 'boolean',
                  isPrivate: false,
                  cc: 3,
                  coverage: 0.92,
                  crap: 2.1,
                  killed: 6,
                  survived: 0,
                  uncovered: 0,
                  line: 24
                }
              ]
            }
          ]
        },
        {
          id: 'application',
          label: 'application.clinic (Ring 1)',
          level: 1,
          crap: { mu: 1.8, max: 2.5, sigma: 0.3 },
          mutationScore: 0.92,
          childPackageIds: ['org.springframework.samples.petclinic.application'],
          classes: [
            {
              id: 'org.springframework.samples.petclinic.application.ScheduleVisitUseCase',
              name: 'ScheduleVisitUseCase',
              packageName: 'org.springframework.samples.petclinic.application',
              filePath: 'src/main/java/org/springframework/samples/petclinic/application/ScheduleVisitUseCase.java',
              stereotype: 'CLASS',
              isForeign: false,
              level: 1,
              crap: { mu: 1.8, max: 2.5, sigma: 0.3 },
              coverage: 0.95,
              cc: 4,
              killed: 8,
              survived: 0,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        },
        {
          id: 'adapters',
          label: 'adapters.web (Ring 2)',
          level: 2,
          crap: { mu: 1.5, max: 2.0, sigma: 0.2 },
          mutationScore: 0.9,
          childPackageIds: ['org.springframework.samples.petclinic.adapters'],
          classes: [
            {
              id: 'org.springframework.samples.petclinic.adapters.VisitController',
              name: 'VisitController',
              packageName: 'org.springframework.samples.petclinic.adapters',
              filePath: 'src/main/java/org/springframework/samples/petclinic/adapters/VisitController.java',
              stereotype: 'CLASS',
              isForeign: false,
              level: 2,
              crap: { mu: 1.5, max: 2.0, sigma: 0.2 },
              coverage: 0.9,
              cc: 2,
              killed: 5,
              survived: 0,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        },
        {
          id: 'infrastructure',
          label: 'infrastructure.jpa (Ring 3)',
          level: 3,
          crap: { mu: 2.4, max: 3.8, sigma: 0.5 },
          mutationScore: 0.88,
          childPackageIds: ['org.springframework.samples.petclinic.infrastructure'],
          classes: [
            {
              id: 'org.springframework.samples.petclinic.infrastructure.JpaVetRepository',
              name: 'JpaVetRepository',
              packageName: 'org.springframework.samples.petclinic.infrastructure',
              filePath: 'src/main/java/org/springframework/samples/petclinic/infrastructure/JpaVetRepository.java',
              stereotype: 'CLASS',
              isForeign: false,
              level: 3,
              crap: { mu: 2.4, max: 3.8, sigma: 0.5 },
              coverage: 0.88,
              cc: 4,
              killed: 7,
              survived: 1,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        }
      ],
      edges: [
        {
          from: 'org.springframework.samples.petclinic.application.ScheduleVisitUseCase',
          to: 'org.springframework.samples.petclinic.domain.Vet',
          kind: 'DEPENDENCY',
          label: 'schedules',
          isViolating: false
        },
        {
          from: 'org.springframework.samples.petclinic.adapters.VisitController',
          to: 'org.springframework.samples.petclinic.application.ScheduleVisitUseCase',
          kind: 'DEPENDENCY',
          label: 'invokes use case',
          isViolating: false
        },
        {
          from: 'org.springframework.samples.petclinic.domain.VetSpecialtyValidator',
          to: 'org.springframework.samples.petclinic.infrastructure.JpaVetRepository',
          kind: 'DEPENDENCY',
          label: 'Outward Ring 0 -> Ring 3 Violation (Invert with VetRepositoryPort)',
          isViolating: true
        }
      ]
    }
  },
  {
    id: 'quarkus-quickstart',
    name: 'Quarkus Reactive Microservice',
    subtitle: 'Conforming Hexagonal Ports & Adapters microservice (0 violations, Grade A fitness)',
    language: 'java',
    policy: {
      title: 'Quarkus Order Microservice',
      src: 'src/main/java',
      prefix: 'io.quarkus.samples.order',
      hierarchical: true,
      order: ['domain', 'usecase', 'delivery'],
      levels: [['domain'], ['usecase'], ['delivery']],
      foreign: ['java.', 'jakarta.', 'io.quarkus.'],
      omit: ['target'],
      lang: 'java',
      proposals: []
    },
    graph: {
      title: 'Quarkus Order Microservice',
      isProposal: false,
      unassigned: [],
      screamingMetric: {
        score: 1.0,
        domainPackageCount: 3,
        technicalPackageCount: 0,
        totalPackageCount: 3,
        classification: 'PACKAGE_BY_FEATURE',
        domainPackages: ['domain', 'usecase', 'delivery'],
        technicalPackages: [],
        frameworkGravityWarnings: []
      },
      cycles: [],
      components: [
        {
          id: 'domain',
          label: 'domain (Ring 0)',
          level: 0,
          crap: { mu: 1.1, max: 1.5, sigma: 0.1 },
          mutationScore: 0.98,
          childPackageIds: ['io.quarkus.samples.order.domain'],
          classes: [
            {
              id: 'io.quarkus.samples.order.domain.Order',
              name: 'Order',
              packageName: 'io.quarkus.samples.order.domain',
              filePath: 'src/main/java/io/quarkus/samples/order/domain/Order.java',
              stereotype: 'RECORD',
              isForeign: false,
              level: 0,
              crap: { mu: 1.0, max: 1.0, sigma: 0.0 },
              coverage: 1.0,
              cc: 1,
              killed: 4,
              survived: 0,
              uncovered: 0,
              fields: [],
              methods: []
            },
            {
              id: 'io.quarkus.samples.order.domain.OrderGatewayPort',
              name: 'OrderGatewayPort',
              packageName: 'io.quarkus.samples.order.domain',
              filePath: 'src/main/java/io/quarkus/samples/order/domain/OrderGatewayPort.java',
              stereotype: 'INTERFACE',
              isForeign: false,
              level: 0,
              crap: { mu: 1.0, max: 1.0, sigma: 0.0 },
              coverage: 1.0,
              cc: 1,
              killed: 2,
              survived: 0,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        },
        {
          id: 'usecase',
          label: 'usecase (Ring 1)',
          level: 1,
          crap: { mu: 1.4, max: 2.0, sigma: 0.2 },
          mutationScore: 0.95,
          childPackageIds: ['io.quarkus.samples.order.usecase'],
          classes: [
            {
              id: 'io.quarkus.samples.order.usecase.PlaceOrderUseCase',
              name: 'PlaceOrderUseCase',
              packageName: 'io.quarkus.samples.order.usecase',
              filePath: 'src/main/java/io/quarkus/samples/order/usecase/PlaceOrderUseCase.java',
              stereotype: 'CLASS',
              isForeign: false,
              level: 1,
              crap: { mu: 1.4, max: 2.0, sigma: 0.2 },
              coverage: 0.96,
              cc: 2,
              killed: 6,
              survived: 0,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        },
        {
          id: 'delivery',
          label: 'delivery (Ring 2)',
          level: 2,
          crap: { mu: 1.6, max: 2.1, sigma: 0.3 },
          mutationScore: 0.93,
          childPackageIds: ['io.quarkus.samples.order.delivery'],
          classes: [
            {
              id: 'io.quarkus.samples.order.delivery.OrderResource',
              name: 'OrderResource',
              packageName: 'io.quarkus.samples.order.delivery',
              filePath: 'src/main/java/io/quarkus/samples/order/delivery/OrderResource.java',
              stereotype: 'CLASS',
              isForeign: false,
              level: 2,
              crap: { mu: 1.6, max: 2.1, sigma: 0.3 },
              coverage: 0.94,
              cc: 2,
              killed: 5,
              survived: 0,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        }
      ],
      edges: [
        {
          from: 'io.quarkus.samples.order.usecase.PlaceOrderUseCase',
          to: 'io.quarkus.samples.order.domain.OrderGatewayPort',
          kind: 'DEPENDENCY',
          label: 'depends on port',
          isViolating: false
        },
        {
          from: 'io.quarkus.samples.order.delivery.OrderResource',
          to: 'io.quarkus.samples.order.usecase.PlaceOrderUseCase',
          kind: 'DEPENDENCY',
          label: 'REST -> UseCase',
          isViolating: false
        }
      ]
    }
  },
  {
    id: 'realworld-conduit',
    name: 'RealWorld Conduit (Full-Stack TS + FastAPI)',
    subtitle: 'Cross-language UI-to-API boundary tracing (HTTP /api/articles) & ADP cycle detection',
    language: 'typescript',
    policy: {
      title: 'RealWorld Conduit Polyglot Spec',
      src: 'src',
      prefix: 'conduit',
      hierarchical: true,
      order: ['domain', 'application', 'adapters', 'frontend'],
      levels: [['domain'], ['application'], ['adapters'], ['frontend']],
      foreign: [],
      omit: ['node_modules'],
      lang: 'typescript',
      proposals: []
    },
    graph: {
      title: 'RealWorld Conduit Polyglot Spec',
      isProposal: false,
      unassigned: [],
      screamingMetric: {
        score: 0.75,
        domainPackageCount: 3,
        technicalPackageCount: 1,
        totalPackageCount: 4,
        classification: 'PACKAGE_BY_FEATURE',
        domainPackages: ['domain', 'application', 'frontend'],
        technicalPackages: ['adapters'],
        frameworkGravityWarnings: []
      },
      cycles: [
        {
          packages: ['application', 'adapters']
        }
      ],
      components: [
        {
          id: 'domain',
          label: 'domain.article (Ring 0)',
          level: 0,
          crap: { mu: 1.2, max: 1.5, sigma: 0.1 },
          mutationScore: 0.95,
          childPackageIds: ['conduit.domain'],
          classes: [
            {
              id: 'conduit.domain.Article',
              name: 'Article',
              packageName: 'conduit.domain',
              filePath: 'backend/conduit/domain/article.py',
              stereotype: 'CLASS',
              isForeign: false,
              level: 0,
              crap: { mu: 1.2, max: 1.5, sigma: 0.1 },
              coverage: 0.96,
              cc: 2,
              killed: 5,
              survived: 0,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        },
        {
          id: 'application',
          label: 'application.feed (Ring 1)',
          level: 1,
          crap: { mu: 2.3, max: 3.1, sigma: 0.4 },
          mutationScore: 0.89,
          childPackageIds: ['conduit.application'],
          classes: [
            {
              id: 'conduit.application.ArticleFeedService',
              name: 'ArticleFeedService',
              packageName: 'conduit.application',
              filePath: 'backend/conduit/application/feed_service.py',
              stereotype: 'CLASS',
              isForeign: false,
              level: 1,
              crap: { mu: 2.3, max: 3.1, sigma: 0.4 },
              coverage: 0.89,
              cc: 3,
              killed: 6,
              survived: 1,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        },
        {
          id: 'adapters',
          label: 'adapters.api (Ring 2)',
          level: 2,
          crap: { mu: 1.9, max: 2.5, sigma: 0.3 },
          mutationScore: 0.91,
          childPackageIds: ['conduit.adapters'],
          classes: [
            {
              id: 'conduit.adapters.ArticleRouter',
              name: 'ArticleRouter',
              packageName: 'conduit.adapters',
              filePath: 'backend/conduit/adapters/article_router.py',
              stereotype: 'CLASS',
              isForeign: false,
              level: 2,
              crap: { mu: 1.9, max: 2.5, sigma: 0.3 },
              coverage: 0.91,
              cc: 2,
              killed: 4,
              survived: 0,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        },
        {
          id: 'frontend',
          label: 'frontend.ui (Ring 3)',
          level: 3,
          crap: { mu: 1.7, max: 2.2, sigma: 0.2 },
          mutationScore: 0.92,
          childPackageIds: ['conduit.frontend'],
          classes: [
            {
              id: 'conduit.frontend.ArticleFeedClient',
              name: 'ArticleFeedClient',
              packageName: 'conduit.frontend',
              filePath: 'frontend/src/api/ArticleFeedClient.ts',
              stereotype: 'CLASS',
              isForeign: false,
              level: 3,
              crap: { mu: 1.7, max: 2.2, sigma: 0.2 },
              coverage: 0.92,
              cc: 2,
              killed: 5,
              survived: 0,
              uncovered: 0,
              fields: [],
              methods: []
            }
          ]
        }
      ],
      edges: [
        {
          from: 'conduit.application.ArticleFeedService',
          to: 'conduit.domain.Article',
          kind: 'DEPENDENCY',
          label: 'queries articles',
          isViolating: false
        },
        {
          from: 'conduit.adapters.ArticleRouter',
          to: 'conduit.application.ArticleFeedService',
          kind: 'DEPENDENCY',
          label: 'FastAPI route',
          isViolating: false
        },
        {
          from: 'conduit.application.ArticleFeedService',
          to: 'conduit.adapters.ArticleRouter',
          kind: 'DEPENDENCY',
          label: 'ADP Cycle & Outward Violation',
          isViolating: true
        },
        {
          from: 'conduit.frontend.ArticleFeedClient',
          to: 'conduit.adapters.ArticleRouter',
          kind: 'DEPENDENCY',
          label: 'HTTP /api/articles',
          isViolating: false
        }
      ]
    }
  }
];

