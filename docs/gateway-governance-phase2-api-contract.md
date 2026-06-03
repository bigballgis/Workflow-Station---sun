# Gateway Governance Phase 2 API Contract

Base path: `/api/v1/admin/gateway` (served by `gateway-management-service` after extraction)

---

## 1. Drift Detection

### Get Drift Reports

- **GET** `/drift/reports?environmentCode=DEV&page=0&size=20`

### Trigger Drift Sync

- **POST** `/drift/sync`
```json
{ "environmentCode": "DEV" }
```

### Get Drift Report Detail

- **GET** `/drift/reports/{reportId}`

Response includes: `missing`, `extra`, `mismatch` arrays with normalized business objects (not Kong native names).

---

## 2. Monitoring

### Overview

- **GET** `/monitoring/overview?environmentCode=DEV&period=1h`

```json
{
  "qps": 1200,
  "p50LatencyMs": 45,
  "p95LatencyMs": 180,
  "errorRate": 0.02,
  "topApis": [],
  "topApplications": []
}
```

### API Metrics

- **GET** `/monitoring/apis/{apiId}?environmentCode=DEV&period=24h`

---

## 3. Release Promotion

### Promote to Next Environment

- **POST** `/releases/{releaseId}/promote`

```json
{
  "targetEnvironmentCode": "SIT",
  "description": "Promote from DEV"
}
```

### Request PROD Approval

- **POST** `/releases/{releaseId}/request-approval`

```json
{
  "approverRole": "GATEWAY_ADMIN",
  "comment": "Ready for PROD"
}
```

### Approve Release

- **POST** `/releases/{releaseId}/approve`

```json
{ "approved": true, "comment": "Approved" }
```

---

## 4. Enhanced Policies

### OAuth2 Access Policy

```json
{
  "type": "OAUTH2",
  "config": {
    "tokenEndpoint": "https://auth.example.com/oauth/token",
    "scopes": ["api:read"]
  }
}
```

### ACL Access Policy

```json
{
  "type": "ACL",
  "config": {
    "allow": ["internal-apps"],
    "deny": []
  }
}
```

### Canary Traffic Policy

```json
{
  "type": "CANARY",
  "config": {
    "baselineWeight": 90,
    "canaryWeight": 10,
    "canaryUpstreamRef": "order-service-v2:8080"
  }
}
```

---

## 5. Error Codes (Phase 2 Additions)

- `GATEWAY_DRIFT_SYNC_FAILED`
- `GATEWAY_PROMOTION_INVALID_ENV`
- `GATEWAY_APPROVAL_REQUIRED`
- `GATEWAY_APPROVAL_DENIED`
- `GATEWAY_METRICS_UNAVAILABLE`

---

## 6. Control Plane Mapping

This contract remains valid and maps to the canonical model in `release-control-plane-blueprint.md`:

- `/releases/{id}/request-approval` and `/releases/{id}/approve` map to `ApprovalTicket` lifecycle
- `/releases/{id}/promote` maps to cross-environment `ReleasePlan` progression from a published source
- `/drift/*` endpoints map to canonical `DriftReport` model (`desired` vs `actual`)
- `/monitoring/*` responses map to control-plane ops/audit observability views
