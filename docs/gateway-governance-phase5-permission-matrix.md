# Gateway Governance Phase 5 Permission Matrix

Extends Phase 4 for multi-gateway governance.

## New Permission Keys

| Key | Description |
|---|---|
| `gateway:governance:read` | View governance rules |
| `gateway:governance:write` | Create/update/delete rules |
| `gateway:compliance:read` | View compliance check results |
| `gateway:compliance:export` | Export compliance reports |
| `gateway:provider:read` | View environment provider config |
| `gateway:provider:write` | Change environment provider (restricted) |

## Role Mapping

| Permission | GATEWAY_OPERATOR | GATEWAY_ADMIN | SECURITY_AUDITOR | PLATFORM_ADMIN |
|---|---|---|---|---|
| `gateway:governance:read` | Y | Y | Y | Y |
| `gateway:governance:write` | N | Y | N | Y |
| `gateway:compliance:read` | Y | Y | Y | Y |
| `gateway:compliance:export` | N | Y | Y | Y |
| `gateway:provider:read` | Y | Y | Y | Y |
| `gateway:provider:write` | N | Y | N | Y |

## Sensitive Operations

| Action | Permission | Extra Gate |
|---|---|---|
| Switch PROD provider | `gateway:provider:write` | Dual approval recommended |
| Disable BLOCK rule in PROD | `gateway:governance:write` | Audit + approval |
| Export compliance report | `gateway:compliance:export` | Tenant scope enforced |

## Menu Additions

| Route | Permission |
|---|---|
| `/gateway/governance/rules` | `gateway:governance:read` |
| `/gateway/governance/compliance` | `gateway:compliance:read` |
