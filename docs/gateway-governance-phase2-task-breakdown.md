# Gateway Governance Phase 2 Task Breakdown

## 1. Scope

Phase 2 builds on Phase 1 (`gateway-governance-phase1-blueprint.md`):

- Extract `gateway-management-service`
- Drift detection, monitoring, release promotion, policy enhancements

---

## 2. Milestones

| Milestone | Target | Deliverable |
|---|---|---|
| M1 | W1 | GMS extracted and routable |
| M2 | W2 | Drift report-only operational |
| M3 | W3 | Monitoring dashboard + promotion |
| M4 | W4 | Prod approval + policy completion |

---

## 3. Workstreams

### BE-1: Service Extraction (P0)
- Create `backend/gateway-management-service` Maven module
- Move gateway packages from admin-center
- Update Kong route: `/api/v1/admin/gateway/*` -> GMS
- **Acceptance**: Phase 1 APIs work against GMS endpoint

### BE-2: Drift Detector (P0)
- `DriftSyncJob` scheduled per environment
- `DriftReport` entity + API
- Compare desired (latest published) vs Kong runtime
- **Acceptance**: Drift report lists missing/extra/mismatch items

### BE-3: Monitoring Aggregator (P1)
- Integrate Kong Prometheus or access log metrics
- API: `GET /gateway/monitoring/overview`, `GET /gateway/monitoring/apis/{id}`
- **Acceptance**: Dashboard shows QPS, latency, error rate

### BE-4: Release Promotion (P0)
- `POST /releases/{id}/promote` with target environment
- Promotion copies snapshot to next env release
- **Acceptance**: DEV release promotable to SIT

### BE-5: Prod Approval Gate (P0)
- `POST /releases/{id}/request-approval`
- Block PROD publish until approved
- **Acceptance**: Unapproved PROD publish rejected

### BE-6: Policy Enhancements (P1)
- OAuth2, ACL, Canary, Blue-Green mapping in Kong adapter
- **Acceptance**: Policy types in API contract mappable to Kong

### FE-1: Drift UI (P1)
- `/gateway/drift` page with environment selector and report list
- **Acceptance**: User views drift without manual Kong access

### FE-2: Monitoring Dashboard (P1)
- `/gateway/monitoring` with charts (QPS, latency, errors)
- **Acceptance**: Metrics refresh for published APIs

### FE-3: Promotion UI (P0)
- Release detail: Promote button, approval status
- **Acceptance**: Full DEV->SIT promotion from UI

### DB-1: Phase 2 Schema (P0)
- See `gateway-governance-phase2-ddl.sql`
- **Acceptance**: Migration applies cleanly

### QA-1: E2E (P0)
- Publish -> drift check -> promote -> prod approval block
- **Acceptance**: E2E script passes in dev

---

## 4. Dependencies

1. Phase 1 complete
2. DB-1 before BE-2/BE-4
3. BE-1 before all other BE tasks
4. BE APIs before FE pages

---

## 5. Definition of Done

- GMS independently deployable
- Drift reports generated per environment
- Monitoring dashboard operational
- Promotion and prod approval enforced
- Phase 1 regression tests pass
