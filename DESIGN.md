---
name: Archlens
description: Real-time Clean Architecture governance and polyglot dependency inspection workbench
colors:
  primary: "#3b82f6"
  primary-hover: "#2563eb"
  accent-emerald: "#10b981"
  accent-amber: "#f59e0b"
  accent-rose: "#ef4444"
  neutral-bg: "#020617"
  surface-dark: "#0f172a"
  surface-card: "#1e293b"
  surface-border: "#334155"
  text-primary: "#f8fafc"
  text-secondary: "#94a3b8"
  text-muted: "#64748b"
typography:
  display:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  body:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  mono:
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: "normal"
rounded:
  sm: "4px"
  md: "8px"
  lg: "12px"
  full: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.md}"
    padding: "6px 12px"
  button-surface:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.text-secondary}"
    rounded: "{rounded.md}"
    padding: "6px 12px"
---

# Archlens Design System

## Overview

Archlens is an architectural engineering workstation designed for developers, architects, and autonomous coding agents. The design tone is technical, dark-mode first, high-density, and precise. It draws inspiration from IDE diagnostic tools, radar telemetry, and Robert C. Martin's concentric Clean Architecture visualization.

## Colors

- **Core Canvas**: Slate-950 (`#020617`) canvas background providing maximum contrast for dependency flow paths.
- **Surfaces**: Slate-900 (`#0f172a`) headers and sidebars; Slate-800 (`#1e293b`) interactive component cards.
- **Architectural Conformance**:
  - Emerald (`#10b981`): Conforming inward dependency flows, low CRAP score ($\le 4$), high test coverage ($\ge 80\%$), healthy mutation test metrics.
  - Amber (`#f59e0b`): What-If virtual proposals, moderate complexity (CRAP 5–15).
  - Rose / Red (`#ef4444`): Outward Clean Architecture Dependency Rule breaches, high complexity (CRAP $> 15$), low test coverage.
- **Accents**: Blue-500 (`#3b82f6`) for selected/focused nodes, active filters, and primary workbench triggers.

## Typography

- **UI Headings & Labels**: Clean system sans-serif (`Inter`, system UI) with tight tracking and medium/semibold weight for maximum scanability.
- **Source Code & Identifiers**: Pure monospace (`ui-monospace`, `Menlo`, `Monaco`) for class identifiers, line numbers, and file paths.
- **Badges & Metrics**: Monospace bold at micro-scales (`text-[9px]` or `text-[10px]`) for CRAP numbers and ring tiers.

## Layout

- **Three-Zone Workstation**:
  1. **Top Header Bar**: 48px fixed height housing workbench title, project switcher, build badges, and Quick Find (`Cmd+K`).
  2. **Infinite 2D SVG Canvas**: Primary viewport supporting smooth GSAP camera pans, zoom levels from 20% to 300%, and Sugiyama-style layered concentric tier lanes.
  3. **Right Inspector Panel**: 320px collapsible sidebar containing architectural views, proposal triggers, display controls, and Clean Architecture rule legends.
  4. **Bottom Telemetry Dock**: Collapsible telemetry drawer displaying real-time `.uml-viewer/` IPC mailbox activity.

## Elevation & Depth

- **Tonal Layering**: Depth is achieved through border definitions (`border-slate-800`), dark background layering, and backdrop blurs (`backdrop-blur-md`) rather than heavy drop shadows.
- **Active Focus**: Focused components receive a vibrant outline glow (`stroke-blue-400` with subtle glow filters) to stand out from dimmed background elements.

## Shapes

- **Component Boxes**: 8px rounded corners (`rounded-lg`) with distinct draggable top header banners (`rounded-t-lg`).
- **Interactive Badges**: 4px rounded pills (`rounded`) with muted transparent fills (`bg-slate-800/80` or `bg-emerald-500/20`).

## Components

- **Canvas Nodes (`ComponentBox.svelte`)**: Tier-ranked component boxes displaying package name, ring badge, and top classes with dynamic CRAP/coverage indicators.
- **Dependency Edges (`DependencyEdge.svelte`)**: Curved cubic Bezier SVG paths with animated dash-flow arrows. Inward conforming flows are muted gray-blue; outward violations are vibrant pulsing red.
- **Command Palette (`CommandPalette.svelte`)**: Centered glassmorphic modal with instant fuzzy search across components, classes, and actions.
- **Source Code Viewer (`SourceModal.svelte`)**: Fullscreen monospace code inspection modal with exact line numbers, copy button, and active target-line highlighting.
- **What-If Diff Banner (`ProposalDiffBanner.svelte`)**: Floating glassmorphic notification banner displaying the real-time architectural impact of virtual proposals.

## Do's and Don'ts

### Do's
- Keep text colors solid and crisp; maintain high contrast on dark slate backgrounds.
- Use monospace fonts for all class names, file paths, metrics, and line numbers.
- Provide keyboard shortcuts for primary navigation actions (`Cmd+K`, `P`, `D`, `T`, `Esc`).
- Ensure all animations (camera pan, halo pulse, edge hover) are hardware-accelerated (`transform`, `opacity`).

### Don'ts
- Don't use decorative gradient text (`bg-clip-text`) or artificial AI tells.
- Don't use thick card side-tabs (`border-l-4`).
- Don't animate layout properties (`width`, `height`, `top`, `left`) during canvas interactions; use SVG `transform` or CSS transforms.
- Don't introduce external cloud services or tracking; keep the entire workbench self-contained and local.
