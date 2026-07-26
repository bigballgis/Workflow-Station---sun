# Project X-Ray — Workflow Station

A production-grade, reverse-engineered anatomy of the platform: architecture, module dissection, page/button-level behaviour, feature-completion matrix, logic-closure analysis, and a full risk/security/testing audit. Every claim is grounded in code, schema, or config; unverifiable claims are labelled `Unknown`.

**Start here → [PROJECT-X-RAY.md](PROJECT-X-RAY.md)** (master synthesis: executive summary, scorecard, system/high-level diagrams, feature matrix, journeys, logic-closure, security, roadmap).

## Companion deep-dives

### architecture/
- [database.md](architecture/database.md) — 115 tables, ER diagrams, JSON-row storage, migration hygiene
- [authentication-authorization.md](architecture/authentication-authorization.md) — JWT/SSO/LDAP flows, RBAC enforcement, security gaps
- [workflow-engine.md](architecture/workflow-engine.md) — Flowable 7 lifecycle, service tasks, endpoint inventory, broken loops
- [ai-and-integrations.md](architecture/ai-and-integrations.md) — AI FU-generation, Activepieces, n8n (dead), Superset, Kong
- [infrastructure-deployment.md](architecture/infrastructure-deployment.md) — k8s/Istio topology, env vars, secrets audit, observability

### modules/
- [admin-center.md](modules/admin-center.md) — governance, deployment target, BI, LDAP, audit
- [developer-workstation.md](modules/developer-workstation.md) — design-time FU designer (tabs, endpoints, gaps)
- [user-portal.md](modules/user-portal.md) — end-user runtime, form engine, tasks, runtime data-flow

### audit/
- [shared-and-crosscutting.md](audit/shared-and-crosscutting.md) — shared JARs, frontend infra, dead-code inventory
- [testing-and-quality.md](audit/testing-and-quality.md) — test coverage, God-classes, fallback debt, tooling gaps

> Generated 2026-07 from branch `common_0701_timeline`. Boundary source of truth: [`../architecture/architecture-blueprint.md`](../architecture/architecture-blueprint.md).
