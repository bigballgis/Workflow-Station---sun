# Release Control Plane API Mapping

## 1. Purpose

This document maps existing MFE and Gateway governance APIs to the canonical model in `release-control-plane-blueprint.md`.

It is a semantic mapping layer. Existing endpoints stay backward compatible.

---

## 2. Canonical Objects Reference

- `ReleasePlan`
- `ReleaseExecution`
- `ApprovalTicket`
- `PolicyDecision`
- `DriftReport`
- `AuditEvent`

---

## 3. MFE API Mapping

Base path: `/api/v1/admin/frontend-modules`

| Existing Endpoint | Canonical Mapping | Notes |
|---|---|---|
| `POST /{id}/switch-version` | `ReleasePlan` transition + `release.publish.*` events | Switches active runtime target for a module |
| `POST /{id}/rollback-version` | rollback action (`release.rollback.*`) | Uses target or historical stable version |
| `GET /{id}/versions` | `ReleaseExecution` history/read model | Source for rollback and audit timeline |
| `POST /{id}/health-check` | pre-publish/runtime readiness signal | Input to policy/ops gate decisions |
| `GET /runtime` | runtime projection read model | Domain payload includes host/route/remote metadata |
| `GET/POST/PUT /policies*` (Phase 4) | `PolicyDecision` governance layer | `PASS/WARN/BLOCK` semantics via policy engine |
| `GET /ops/overview` / `GET /ops/modules/{moduleCode}/events` | `AuditEvent` and ops observability | Shared event taxonomy, MFE-specific payload |

---

## 4. Gateway API Mapping

Base path: `/api/v1/admin/gateway`

| Existing Endpoint | Canonical Mapping | Notes |
|---|---|---|
| `POST /releases/{releaseId}/publish` | `ReleaseExecution` publish path | Provider-specific revision remains domain payload |
| `POST /releases/{releaseId}/rollback` | rollback action (`release.rollback.*`) | State transition with execution record |
| `POST /releases/{releaseId}/promote` | cross-environment `ReleasePlan` progression | Typical chain: DEV -> SIT -> UAT -> PROD |
| `POST /releases/{releaseId}/request-approval` | `ApprovalTicket` create/request | Approval gate before sensitive publish |
| `POST /releases/{releaseId}/approve` | `ApprovalTicket` decision | Produces approval decision events |
| `POST /drift/sync` / `GET /drift/reports*` | `DriftReport` model | `desired` vs `actual` normalized business view |
| `GET /monitoring/overview` / `GET /monitoring/apis/{apiId}` | ops observability view | Feeds control-plane dashboards |
| `POST /releases/{id}/compliance-check` (Phase 5) | `PolicyDecision` pre-publish evaluation | BLOCK/WARN/PASS semantics |
| `POST/PUT/DELETE /governance/rules*` (Phase 5) | policy rule management | Backing rules for policy gate decisions |

---

## 5. Cross-Domain Event Taxonomy

Both domains should emit/align to shared event names where applicable:

- `release.created`
- `release.validated`
- `release.approval.requested`
- `release.approval.decided`
- `release.publish.started`
- `release.publish.succeeded`
- `release.publish.failed`
- `release.rollback.started`
- `release.rollback.succeeded`
- `release.rollback.failed`
- `policy.evaluated`
- `drift.detected`

---

## 6. Compatibility Rules

1. Do not remove existing domain endpoints in current phases.
2. Additive mapping only; semantic unification precedes endpoint unification.
3. Keep domain payload fields:
   - MFE: `hostApp`, `moduleCode`, `remoteEntryUrl`, `routePath`, `exposedModule`
   - Gateway: `provider`, `runtimeRevision`, policy/provider configuration
4. New Phase 4+ APIs must declare canonical mapping in their contract docs.

---

## 7. Backfill Status (2026-05-28)

Canonical contract backfill is **COMPLETE**. See `release-control-plane-backfill.md` for details.

Implemented:
- `com.admin.controlplane.*` — canonical model (events, states, error codes)
- Gateway services emit canonical events on all release lifecycle operations
- MFE services emit canonical events on switch/rollback operations
- Sample `ControlPlaneEventListener` demonstrates cross-domain consumption pattern
