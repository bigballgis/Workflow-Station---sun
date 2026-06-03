# Gateway Governance Phase 3 API Contract

Phase 3 is primarily a frontend architecture change. **No breaking changes** to GMS API contract from Phase 2.

---

## 1. Host-Remote Integration Contract

### Remote Entry

- Host env: `VITE_GATEWAY_MFE_URL=https://gateway-mfe.example.com/assets/remoteEntry.js`
- Remote exposes: `./GatewayApp`, `./GatewayRoutes`

### Shared SDK (platform package)

| Export | Purpose |
|---|---|
| `usePermission()` | RBAC checks in remote |
| `useAuth()` | Session / user context |
| `useTheme()` | Theme tokens |
| `useI18nBridge()` | Locale sync host ↔ remote |

---

## 2. Optional Host Proxy (if needed)

If host proxies gateway API during transition:

- **GET** `/api/v1/admin/gateway/*` → forward to GMS
- Headers preserved: `Authorization`, `X-User-Id`, `X-Username`

---

## 3. Version Pin API (Host Config)

Host reads remote version from config:

```yaml
gateway:
  mfe:
    url: https://gateway-mfe.example.com/assets/remoteEntry.js
    version: "1.2.0"  # pin for rollback
```

No new backend endpoints required for Phase 3 MVP.

---

## 4. Phase 3 Non-Goals (API)

- No new GMS business endpoints
- No marketplace APIs (Phase 4)
- No multi-provider APIs (Phase 5)
