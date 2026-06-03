# MFE Governance Phase 1 Permission Matrix

## 1. Permission Keys

| Key | Description |
|---|---|
| `frontend.module:read` | View module registry list |
| `frontend.module:write` | Create/update module config |
| `frontend.module:enable` | Enable/disable module |
| `frontend.module:version:switch` | Switch module version |
| `frontend.module:version:rollback` | Rollback module version |
| `frontend.module:runtime:read` | Query runtime module config |

---

## 2. Suggested Roles

| Role | Responsibility |
|---|---|
| `FRONTEND_MODULE_VIEWER` | Read-only module governance |
| `FRONTEND_MODULE_OPERATOR` | Daily config and enable operations |
| `FRONTEND_MODULE_ADMIN` | Full control including version rollback |
| `SECURITY_AUDITOR` | Audit-only access |

---

## 3. Role to Permission Mapping

| Permission | VIEWER | OPERATOR | ADMIN | AUDITOR |
|---|---|---|---|---|
| `frontend.module:read` | Y | Y | Y | Y |
| `frontend.module:write` | N | Y | Y | N |
| `frontend.module:enable` | N | Y | Y | N |
| `frontend.module:version:switch` | N | Y | Y | N |
| `frontend.module:version:rollback` | N | N | Y | N |
| `frontend.module:runtime:read` | N | Y | Y | Y |

---

## 4. Operation Matrix

| Action | Permission |
|---|---|
| Query registry list | `frontend.module:read` |
| Create module config | `frontend.module:write` |
| Update module config | `frontend.module:write` |
| Enable module | `frontend.module:enable` |
| Disable module | `frontend.module:enable` |
| Switch module version | `frontend.module:version:switch` |
| Rollback module version | `frontend.module:version:rollback` |
| Query runtime config API | `frontend.module:runtime:read` |

---

## 5. High-risk Operations (Extra Controls)

- PROD environment `remote_entry_url` change
- PROD environment `route_path` change
- disabling critical module in PROD
- rollback in PROD

Recommended controls:
- stronger approval gate
- mandatory audit record
- dual authorization (optional)

