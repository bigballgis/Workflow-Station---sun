# MFE Governance Phase 4 Permission Matrix

## New Permissions

| Key | Description |
|---|---|
| `frontend.module:policy:read` | view rollout policies |
| `frontend.module:policy:write` | manage rollout policies |
| `frontend.module:ops:read` | view ops dashboard/events |
| `frontend.module:audit:export` | export module governance audit |

## Role Mapping

| Permission | OPERATOR | ADMIN | AUDITOR |
|---|---|---|---|
| `frontend.module:policy:read` | Y | Y | Y |
| `frontend.module:policy:write` | N | Y | N |
| `frontend.module:ops:read` | Y | Y | Y |
| `frontend.module:audit:export` | N | Y | Y |

