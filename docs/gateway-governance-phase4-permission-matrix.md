# Gateway Governance Phase 4 Permission Matrix

Extends Phase 2 for Developer API Marketplace.

## New Permission Keys

| Key | Description |
|---|---|
| `gateway:catalog:read` | Browse API catalog (Developer) |
| `gateway:subscription:request` | Submit subscription request |
| `gateway:subscription:read` | View own subscriptions |
| `gateway:subscription:approve` | Approve/reject subscription requests |
| `gateway:subscription:revoke` | Revoke active subscription |
| `gateway:catalog:admin` | Manage catalog visibility |

## Role Mapping (Additions)

| Permission | DEVELOPER | GATEWAY_OPERATOR | GATEWAY_ADMIN | APPROVER |
|---|---|---|---|---|
| `gateway:catalog:read` | Y | Y | Y | Y |
| `gateway:subscription:request` | Y | N | Y | N |
| `gateway:subscription:read` | Y | Y | Y | Y |
| `gateway:subscription:approve` | N | N | Y | Y |
| `gateway:subscription:revoke` | N | Y | Y | N |
| `gateway:catalog:admin` | N | N | Y | N |

> `DEVELOPER` maps to existing developer-workstation workspace roles; wire via platform RBAC.

## Surface Matrix

| Surface | Module | Key Permissions |
|---|---|---|
| API Catalog | Developer Workstation | `gateway:catalog:read` |
| Request Subscription | Developer Workstation | `gateway:subscription:request` |
| My Subscriptions | Developer Workstation | `gateway:subscription:read` |
| Approval Queue | User Portal / Admin | `gateway:subscription:approve` |
| Catalog Admin | gateway-mfe | `gateway:catalog:admin` |
