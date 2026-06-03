# MFE Governance Phase 2 Permission Matrix

## New Permissions

| Key | Description |
|---|---|
| `frontend.module:health:check` | run module health check |
| `frontend.module:version:read` | list module versions |
| `frontend.module:version:switch` | switch active version |
| `frontend.module:version:rollback` | rollback module version |

## Role Mapping

| Permission | OPERATOR | ADMIN | AUDITOR |
|---|---|---|---|
| `frontend.module:health:check` | Y | Y | N |
| `frontend.module:version:read` | Y | Y | Y |
| `frontend.module:version:switch` | Y | Y | N |
| `frontend.module:version:rollback` | N | Y | N |

