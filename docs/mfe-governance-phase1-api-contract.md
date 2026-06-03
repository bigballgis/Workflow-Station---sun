# MFE Governance Phase 1 API Contract

## 1. Overview

Base path: `/api/v1/admin/frontend-modules`

Purpose:
- Admin Center manages frontend module registry
- Host apps fetch runtime-safe module config for dynamic nav/route generation

---

## 2. Data Shape

```json
{
  "id": 101,
  "hostApp": "user-portal",
  "moduleCode": "notification-mfe",
  "displayName": "Notifications",
  "routePath": "/notifications",
  "icon": "Bell",
  "orderNo": 40,
  "remoteEntryUrl": "https://cdn.example.com/notification-mfe/1.0.2/remoteEntry.js",
  "exposedModule": "./App",
  "enabled": true,
  "requiredPermissions": ["notification:read"],
  "tenantScope": ["TENANT_A", "TENANT_B"],
  "env": "DEV",
  "version": "1.0.2"
}
```

---

## 3. Management APIs (Admin)

## 3.1 List

- **GET** `/`
- Query params:
  - `hostApp` (required)
  - `env` (required)
  - `enabled` (optional)
  - `keyword` (optional)

## 3.2 Create

- **POST** `/`

Request:
```json
{
  "hostApp": "user-portal",
  "moduleCode": "notification-mfe",
  "displayName": "Notifications",
  "routePath": "/notifications",
  "icon": "Bell",
  "orderNo": 40,
  "remoteEntryUrl": "https://cdn.example.com/notification-mfe/1.0.2/remoteEntry.js",
  "exposedModule": "./App",
  "requiredPermissions": ["notification:read"],
  "tenantScope": ["TENANT_A"],
  "env": "DEV",
  "version": "1.0.2"
}
```

## 3.3 Update

- **PUT** `/{id}`

## 3.4 Enable / Disable

- **POST** `/{id}/enable`
- **POST** `/{id}/disable`

## 3.5 Switch Version

- **POST** `/{id}/switch-version`

Request:
```json
{
  "version": "1.0.3",
  "remoteEntryUrl": "https://cdn.example.com/notification-mfe/1.0.3/remoteEntry.js"
}
```

## 3.6 Rollback Version

- **POST** `/{id}/rollback-version`

Request:
```json
{
  "targetVersion": "1.0.2"
}
```

---

## 4. Runtime API (Host)

## 4.1 Runtime List

- **GET** `/runtime`
- Query params:
  - `hostApp` (required)
  - `env` (required)

Response:
```json
[
  {
    "moduleCode": "notification-mfe",
    "displayName": "Notifications",
    "routePath": "/notifications",
    "icon": "Bell",
    "orderNo": 40,
    "remoteEntryUrl": "https://cdn.example.com/notification-mfe/1.0.2/remoteEntry.js",
    "exposedModule": "./App",
    "requiredPermissions": ["notification:read"],
    "tenantScope": ["TENANT_A"],
    "version": "1.0.2"
  }
]
```

Notes:
- server should pre-filter disabled configs
- host still performs local permission/tenant checks

---

## 5. Error Codes

- `MFE_MODULE_NOT_FOUND`
- `MFE_MODULE_DUPLICATE_CODE`
- `MFE_ROUTE_CONFLICT`
- `MFE_INVALID_REMOTE_ENTRY`
- `MFE_PERMISSION_DENIED`
- `MFE_INVALID_VERSION_SWITCH`

