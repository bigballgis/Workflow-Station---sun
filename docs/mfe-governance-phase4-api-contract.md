# MFE Governance Phase 4 API Contract

Base path: `/api/v1/admin/frontend-modules`

## Multi-host Runtime

- `GET /runtime?hostApp=admin-center&env=UAT&tenantId=T1`
- `GET /runtime?hostApp=user-portal&env=PROD&tenantId=T2`
- `GET /runtime?hostApp=developer-workstation&env=SIT`

## Governance Policy APIs

- `GET /policies?hostApp=user-portal&env=PROD`
- `POST /policies`
- `PUT /policies/{id}`
- `DELETE /policies/{id}`

## Ops APIs

- `GET /ops/overview?env=PROD`
- `GET /ops/modules/{moduleCode}/events`

## Error Codes

- `MFE_POLICY_BLOCKED`
- `MFE_HOST_NOT_SUPPORTED`
- `MFE_TENANT_SCOPE_DENIED`

## Control Plane Mapping

This contract remains backward compatible and maps to the shared model in `release-control-plane-blueprint.md`:

- policy APIs (`/policies*`) map to canonical `PolicyDecision` semantics (`PASS/WARN/BLOCK`)
- ops event APIs (`/ops/*`) map to canonical release/audit/drift event taxonomy
- host runtime responses keep MFE domain payload fields (`hostApp`, module routing/remote metadata) while following canonical lifecycle vocabulary for release-related operations

