# Gateway Governance Phase 5 Task Breakdown

## 1. Scope

Phase 5: Multi-Gateway API Governance Platform.

Prerequisite: Phase 4 complete (`gateway-governance-phase4-blueprint.md`).

---

## 2. Milestones

| Milestone | Target | Deliverable |
|---|---|---|
| M1 | W1-W2 | APISIX adapter MVP |
| M2 | W3 | Envoy adapter MVP |
| M3 | W4 | Governance rules center |
| M4 | W5 | Per-env provider switching |
| M5 | W6 | Cross-provider drift + stabilization |

---

## 3. Workstreams

### BE-1: APISIX Adapter (P0)
- Implement `ApisixGatewayProvider` per SPI
- Route, upstream, plugin mapping
- **Acceptance**: Publish/rollback works on APISIX dev env

### BE-2: Envoy Adapter (P1)
- Basic xDS or admin API integration
- Route + rate limit mapping
- **Acceptance**: Publish works on Envoy dev env

### BE-3: Provider Factory (P0)
- Resolve adapter by `environment.gateway_provider`
- **Acceptance**: Same release API works for Kong and APISIX

### BE-4: Governance Rules Engine (P0)
- Rule definitions CRUD
- Evaluate at release submit
- Block/warn severity
- **Acceptance**: Invalid PROD release blocked by rules

### BE-5: Compliance Report (P1)
- Export compliance check results
- **Acceptance**: Report downloadable for audit

### BE-6: Cross-Provider Drift (P1)
- Drift detector provider-aware
- **Acceptance**: Drift works for Kong and APISIX environments

### FE-1: Rules Center UI (P0)
- `/gateway/governance/rules` — rule list, create, edit
- **Acceptance**: Admin manages governance rules

### FE-2: Provider Config UI (P1)
- Environment form: provider selector
- **Acceptance**: Admin sets provider per environment

### FE-3: Compliance Dashboard (P1)
- Release detail: compliance check results
- **Acceptance**: User sees block/warn reasons before publish

### DB-1: Phase 5 Schema (P0)
- See `gateway-governance-phase5-ddl.sql`

### QA-1: Multi-Provider E2E (P0)
- Publish same API definition to Kong and APISIX envs
- **Acceptance**: Both succeed with same business model

---

## 4. Definition of Done

- Kong + APISIX adapters operational
- Governance rules block invalid PROD publishes
- Per-environment provider selection works
- Compliance export available for audit
