# Gateway Governance Phase 1 Permission Matrix

## 1. Purpose

This document defines the role-based access model for Gateway Governance Phase 1 in Admin Center.

- Scope: embedded Gateway Domain in `admin-center`
- Reuse current RBAC model and permission style
- Cover menu visibility, route access, and operation-level permissions

---

## 2. Permission Keys (Phase 1)

| Permission Key | Description |
|---|---|
| `gateway:api:read` | View API definitions and versions |
| `gateway:api:write` | Create/update API definitions and versions |
| `gateway:application:read` | View applications and credentials |
| `gateway:application:write` | Create/update applications and credentials |
| `gateway:policy:read` | View access/traffic policies |
| `gateway:policy:write` | Create/update policies |
| `gateway:release:read` | View release details and history |
| `gateway:release:execute` | Submit testing, publish, rollback |
| `gateway:environment:read` | View environment configuration |
| `gateway:environment:write` | Update environment configuration |
| `gateway:audit:read` | View gateway audit logs |

---

## 3. Suggested Roles (Phase 1)

| Role | Responsibility |
|---|---|
| `GATEWAY_VIEWER` | Read-only governance visibility |
| `GATEWAY_OPERATOR` | Daily configuration and release operation |
| `GATEWAY_ADMIN` | Full control including policy/environment |
| `SECURITY_AUDITOR` | Audit and compliance review only |

> Existing platform roles like `SYS_ADMIN` may retain full access as super role.

---

## 4. Role to Permission Mapping

| Permission | GATEWAY_VIEWER | GATEWAY_OPERATOR | GATEWAY_ADMIN | SECURITY_AUDITOR |
|---|---|---|---|---|
| `gateway:api:read` | Y | Y | Y | Y |
| `gateway:api:write` | N | Y | Y | N |
| `gateway:application:read` | Y | Y | Y | Y |
| `gateway:application:write` | N | Y | Y | N |
| `gateway:policy:read` | Y | Y | Y | Y |
| `gateway:policy:write` | N | Y | Y | N |
| `gateway:release:read` | Y | Y | Y | Y |
| `gateway:release:execute` | N | Y | Y | N |
| `gateway:environment:read` | Y | Y | Y | Y |
| `gateway:environment:write` | N | N | Y | N |
| `gateway:audit:read` | N | N | Y | Y |

---

## 5. Menu and Route Matrix

| Menu / Route | Required Permission (any/all) | Notes |
|---|---|---|
| `Gateway Governance` (root menu) | any gateway read permission | hidden if no gateway permission |
| `/gateway/apis` | `gateway:api:read` | API list/detail |
| `/gateway/applications` | `gateway:application:read` | Application list/detail |
| `/gateway/access-policies` | `gateway:policy:read` | Policy list |
| `/gateway/traffic-policies` | `gateway:policy:read` | Policy list |
| `/gateway/releases` | `gateway:release:read` | release list/detail/history |
| `/gateway/environments` | `gateway:environment:read` | env view/update |
| `/gateway/audit` | `gateway:audit:read` | audit query only |

---

## 6. Operation-level Permission Matrix

## API Management

| Action | Permission |
|---|---|
| View API list/detail | `gateway:api:read` |
| Create API | `gateway:api:write` |
| Update API | `gateway:api:write` |
| Create API version | `gateway:api:write` |
| Import OpenAPI | `gateway:api:write` |

## Application Management

| Action | Permission |
|---|---|
| View app list/detail | `gateway:application:read` |
| Create app | `gateway:application:write` |
| Update app | `gateway:application:write` |
| Create credential | `gateway:application:write` |

## Policy Management

| Action | Permission |
|---|---|
| View policy bundle | `gateway:policy:read` |
| Update access policies | `gateway:policy:write` |
| Update traffic policies | `gateway:policy:write` |

## Release Management

| Action | Permission |
|---|---|
| View release/history | `gateway:release:read` |
| Submit testing | `gateway:release:execute` |
| Publish release | `gateway:release:execute` |
| Rollback release | `gateway:release:execute` |

## Environment and Audit

| Action | Permission |
|---|---|
| View environments | `gateway:environment:read` |
| Update environment | `gateway:environment:write` |
| View audit logs | `gateway:audit:read` |

---

## 7. Backend Enforcement Rules

1. UI permission checks are usability controls only.
2. Backend endpoints must enforce permission checks independently.
3. Sensitive actions (`publish`, `rollback`, environment update) require strict backend checks.
4. All denied operations should produce auditable security events.

---

## 8. Audit Requirements by Action Type

| Action Type | Must Audit | Required Fields |
|---|---|---|
| API/Application create/update/delete | Yes | operator, tenant, resource, before/after |
| Policy change | Yes | policy type, before/after json |
| Release submit/publish/rollback | Yes | release id, environment, result, failure reason |
| Permission denied action | Yes | operator, attempted action, endpoint |

---

## 9. Frontend Integration Checklist

- Add gateway permission constants in `src/utils/permission.ts`
- Add route permission mapping for all `/gateway/*` routes
- Add menu visibility checks in `AdminLayout.vue`
- Add button-level guards for create/update/publish/rollback actions
- Hide action buttons if no permission and show disabled-state tooltips when needed

---

## 10. Backend Integration Checklist

- Add permission checks on each gateway controller endpoint
- Enforce operation-level permissions (not only read/write coarse checks)
- Integrate audit logging for success and failure paths
- Ensure tenant isolation for all gateway queries and writes

---

## 11. Phase 1 Exit Criteria (RBAC)

- No gateway menu visible without gateway read permissions
- No privileged action executable without matching write/execute permissions
- All release actions enforce backend permission checks
- All sensitive actions are auditable and queryable

