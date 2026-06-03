# Gateway Governance Phase 4 Task Breakdown

## 1. Scope

Phase 4: Developer API Marketplace.

Prerequisite: Phase 3 complete (`gateway-governance-phase3-blueprint.md`).

---

## 2. Milestones

| Milestone | Target | Deliverable |
|---|---|---|
| M1 | W1 | API Catalog API + DW UI |
| M2 | W2 | Subscription request flow |
| M3 | W3 | Approval + auto-provision |
| M4 | W4 | OpenAPI portal + design bridge |

---

## 3. Workstreams

### BE-1: Catalog API (P0)
- `GET /gateway/catalog/apis` — published APIs for marketplace
- Filter by tenant, environment, visibility
- **Acceptance**: DW can list published APIs

### BE-2: Subscription Request (P0)
- `POST /gateway/subscriptions/request`
- Entity: `ac_gateway_subscription_request`
- **Acceptance**: Request persisted with PENDING status

### BE-3: Workflow Integration (P0)
- Trigger workflow-engine process on request
- Callback on approve/reject
- **Acceptance**: Approval completes and updates request status

### BE-4: Auto-Provision (P0)
- On approve: create access policy + credential via GMS
- Publish to target environment if needed
- **Acceptance**: Approved app can call API

### BE-5: Catalog Visibility Admin (P1)
- Admin API for marketplace visibility rules
- **Acceptance**: Admin hides/shows APIs in catalog

### FE-DW-1: Catalog UI (P0)
- Developer Workstation: API Catalog page
- Browse, filter, view OpenAPI summary
- **Acceptance**: Developer sees published APIs

### FE-DW-2: Subscription UI (P0)
- Request subscription form (app + APIs + env)
- My Subscriptions status page
- **Acceptance**: Full request flow from DW

### FE-UP-1: Approval UI (P0)
- User Portal or Admin: pending subscription approvals
- **Acceptance**: Approver can approve/reject

### FE-GW-1: Marketplace Admin (P1)
- gateway-mfe: catalog visibility management
- **Acceptance**: Admin controls catalog exposure

### DB-1: Phase 4 Schema (P0)
- See `gateway-governance-phase4-ddl.sql`

### QA-1: E2E (P0)
- Request -> approve -> provision -> API callable
- **Acceptance**: E2E passes in dev

---

## 4. Definition of Done

- Developer can subscribe to APIs via marketplace
- Approval workflow provisions access automatically
- OpenAPI docs viewable in catalog
- Audit covers subscription lifecycle
