# Gateway Governance Phase 3 Task Breakdown

## 1. Scope

Phase 3: extract Gateway Domain as `gateway-mfe` micro frontend.

Prerequisite: Phase 2 complete (`gateway-governance-phase2-blueprint.md`).

---

## 2. Milestones

| Milestone | Target | Deliverable |
|---|---|---|
| M1 | W1 | `gateway-mfe` package scaffolded |
| M2 | W2 | Module Federation wired to host |
| M3 | W3 | CI/CD + independent deploy |

---

## 3. Workstreams

### FE-1: Extract Domain Package (P0)
- Create `frontend/gateway-mfe/`
- Move `domains/gateway/*` from admin-center
- **Acceptance**: gateway-mfe builds standalone

### FE-2: Shared Platform SDK (P0)
- Extract `@platform/permission`, `@platform/auth-bridge`, `@platform/theme`
- Host and remote consume same SDK
- **Acceptance**: Permission checks work in remote

### FE-3: Module Federation Config (P0)
- Host: remote entry URL from env
- Remote: expose `./GatewayRoutes`, `./GatewayApp`
- **Acceptance**: Host loads remote at `/gateway/*`

### FE-4: Menu Integration (P0)
- Host menu unchanged; routes lazy-load remote
- **Acceptance**: Navigation identical to Phase 2 UX

### FE-5: monitoring-mfe Scaffold (P2)
- Optional: extract monitoring pages to separate remote stub
- **Acceptance**: Architecture doc only; no prod dependency

### DevOps-1: Build Pipeline (P0)
- Separate Docker image / static asset deploy for gateway-mfe
- Version tag + rollback pin in host env
- **Acceptance**: Deploy gateway-mfe without admin-center rebuild

### QA-1: Integration (P0)
- SSO, RBAC, theme, i18n in remote context
- **Acceptance**: No regression vs embedded mode

---

## 4. Definition of Done

- gateway-mfe loads in Admin Center shell
- Independent deploy and rollback verified
- All Phase 2 gateway features work via remote
