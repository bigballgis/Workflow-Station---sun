# Gateway Governance Phase 2 Permission Matrix

Extends Phase 1 (`gateway-governance-phase1-permission-matrix.md`).

## New Permission Keys

| Key | Description |
|---|---|
| `gateway:drift:read` | View drift reports |
| `gateway:drift:sync` | Trigger drift sync |
| `gateway:monitoring:read` | View monitoring dashboard |
| `gateway:release:approve` | Approve PROD releases |
| `gateway:release:promote` | Promote release across environments |

## Role Mapping (Additions)

| Permission | GATEWAY_VIEWER | GATEWAY_OPERATOR | GATEWAY_ADMIN | SECURITY_AUDITOR |
|---|---|---|---|---|
| `gateway:drift:read` | Y | Y | Y | Y |
| `gateway:drift:sync` | N | Y | Y | N |
| `gateway:monitoring:read` | Y | Y | Y | Y |
| `gateway:release:approve` | N | N | Y | N |
| `gateway:release:promote` | N | Y | Y | N |

## Menu / Route Additions

| Route | Permission |
|---|---|
| `/gateway/drift` | `gateway:drift:read` |
| `/gateway/monitoring` | `gateway:monitoring:read` |

## Operation Matrix

| Action | Permission |
|---|---|
| Trigger drift sync | `gateway:drift:sync` |
| Promote release | `gateway:release:promote` |
| Request PROD approval | `gateway:release:execute` |
| Approve PROD release | `gateway:release:approve` |
