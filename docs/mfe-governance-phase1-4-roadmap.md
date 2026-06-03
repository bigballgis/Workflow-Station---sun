# MFE Governance Phase 1-4 Roadmap

## 1. Purpose

This roadmap provides a single execution view across MFE Governance Phase 1 to Phase 4:

- timeline
- dependencies
- gate criteria
- risk checkpoints

Related index: `mfe-governance-README.md`
Related shared contract: `release-control-plane-blueprint.md`

---

## 2. Phase Summary

| Phase | Focus | Outcome |
|---|---|---|
| Phase 1 | Config-driven foundation | Admin manages module config; host renders dynamic nav/routes |
| Phase 2 | Pilot rollout | 1-2 low-risk MFEs independently deployable |
| Phase 3 | Core workflow extraction | `workflow-mfe` split with parity and rollback readiness |
| Phase 4 | Platform governance | Multi-host governance on top of the shared Release Control Plane contract |

---

## 3. Timeline (Suggested)

```mermaid
gantt
  title MFE Governance Phase 1-4 Roadmap
  dateFormat  YYYY-MM-DD
  excludes    weekends

  section Phase 1 Foundation
  Registry schema + APIs           :p1a, 2026-06-02, 10d
  Admin management UI              :p1b, after p1a, 8d
  Host dynamic nav + route         :p1c, after p1a, 10d
  Audit + rollback hardening       :p1d, after p1b, 6d

  section Phase 2 Pilot
  notification-mfe extraction      :p2a, after p1d, 8d
  delegation-mfe extraction        :p2b, after p1d, 8d
  Independent CI/CD + rollback     :p2c, after p2a, 8d

  section Phase 3 Core
  workflow-mfe boundary freeze     :p3a, after p2c, 6d
  workflow extraction + integration:p3b, after p3a, 15d
  parity/regression + drills       :p3c, after p3b, 8d

  section Phase 4 Platform
  Multi-host governance API        :p4a, after p3c, 10d
  Policy engine + ops dashboard    :p4b, after p4a, 10d
  Cross-host rollout stabilization :p4c, after p4b, 8d
```

---

## 4. Dependency Graph

```mermaid
flowchart LR
  P1[Phase 1 Foundation]
  P2[Phase 2 Pilot Modules]
  P3[Phase 3 workflow-mfe]
  P4[Phase 4 Multi-host Governance]

  P1 --> P2 --> P3 --> P4

  P1 --> D1[Registry SoT]
  P1 --> D2[Runtime API]
  P2 --> D3[Independent CI/CD]
  P3 --> D4[Shared SDK Stability]
  P4 --> D5[Policy + Ops Center]
```

---

## 5. Gate Criteria (Must Pass)

## Phase 1 Gate

- Admin Center can CRUD module config by host/env
- User Portal can display a new nav tag from runtime config only
- dynamic route registration works
- remote loader fallback does not break host shell
- config changes are auditable

## Phase 2 Gate

- at least 2 MFEs (`notification`, `delegation`) independently deployable
- version switch without host redeploy
- rollback drill passes in non-prod
- runtime failure metrics visible

## Phase 3 Gate

- `workflow-mfe` serves To Do / My Request / New Request routes
- no major regression against baseline behavior
- critical version switch protected by permission
- rollback for workflow-mfe validated

## Phase 4 Gate

- governance APIs support `admin-center` / `user-portal` / `developer-workstation`
- policy-based rollout control active
- centralized ops and audit dashboard available
- cross-host release and rollback playbooks verified
- phase outputs are mapped to canonical lifecycle/events in `release-control-plane-blueprint.md`

---

## 6. Risks and Mitigations

| Risk | Typical Phase | Mitigation |
|---|---|---|
| route conflict across modules | P1/P2 | unique route constraint + pre-publish validation |
| remote load failure impacts UX | P1+ | RemoteLoader fallback + timeout + host boundary |
| version drift across envs | P2/P3 | explicit env/version pin + release checklist |
| workflow split regression | P3 | parity test matrix + staged rollout + rollback gate |
| policy complexity explosion | P4 | start with minimal policy set and progressive enforcement |

---

## 7. Operating Model

## Cadence

- weekly architecture sync
- per-phase kickoff and exit review
- release readiness review before each PROD rollout

## Owners

- Platform Architect: boundary and gate decisions
- Frontend Lead: host/remote implementation quality
- Backend Lead: registry/runtime API and audit
- DevOps Lead: pipeline, versioning, rollback automation
- QA Lead: cross-host regression and resilience validation

---

## 8. Recommended First Execution Order

1. Complete Phase 1 gate with `user-portal`
2. Start Phase 2 with `notification-mfe`
3. Add `delegation-mfe` and lock independent release process
4. Enter Phase 3 only after pilot stability is proven
5. Start Phase 4 after workflow-mfe production stabilization

