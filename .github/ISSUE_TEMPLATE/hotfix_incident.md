---
name: Production Hotfix Incident (P0)
about: Critical production incident isolated in a dedicated git worktree
title: "hotfix: "
labels: hotfix, status:ready, priority:critical
---

### 🚨 Incident Summary & Blast Radius
A clear and concise description of what the bug is.

### 🔄 Reproduction & Stack Trace
Steps to reproduce the behavior:
1. Capture failing production payload or stack trace
2. Scaffold failing Red test via `/sdlc reproduce`

### 🎯 Remediation & Rollback Plan
A clear and concise description of what you expected to happen.

### 💻 Environment
- OS: Production
- Canary Health Endpoint: `/health`
