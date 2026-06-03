# Gateway Governance Phase 5 API Contract

Base path: `/api/v1/admin/gateway`

---

## 1. Governance Rules

### List Rules

- **GET** `/governance/rules?environmentCode=PROD`

### Create Rule

- **POST** `/governance/rules`

```json
{
  "ruleCode": "PROD_JWT_REQUIRED",
  "name": "PROD APIs must have JWT",
  "environmentCode": "PROD",
  "severity": "BLOCK",
  "ruleType": "SECURITY",
  "expression": "accessPolicies.any(p => p.type == 'JWT' && p.enabled)",
  "enabled": true
}
```

### Update / Delete Rule

- **PUT** `/governance/rules/{ruleId}`
- **DELETE** `/governance/rules/{ruleId}`

---

## 2. Compliance Evaluation

### Evaluate Release (pre-publish)

- **POST** `/releases/{releaseId}/compliance-check`

Response:

```json
{
  "passed": false,
  "violations": [
    {
      "ruleCode": "PROD_JWT_REQUIRED",
      "severity": "BLOCK",
      "message": "No JWT access policy on API order-query-api v1"
    }
  ],
  "warnings": []
}
```

### Export Compliance Report

- **GET** `/governance/compliance/export?releaseId=3001&format=pdf`

---

## 3. Provider Management

### List Supported Providers

- **GET** `/providers`

```json
["KONG", "APISIX", "ENVOY"]
```

### Update Environment Provider

- **PUT** `/environments/{envId}/provider`

```json
{
  "gatewayProvider": "APISIX",
  "adminEndpoint": "http://apisix-admin:9180",
  "mode": "DB"
}
```

---

## 4. Multi-Provider Publish

Publish API unchanged from Phase 2; adapter resolved by environment:

- **POST** `/releases/{releaseId}/publish`

Response adds:

```json
{
  "provider": "APISIX",
  "runtimeRevision": "apisix-rev-abc123"
}
```

---

## 5. Cross-Provider Drift

- **POST** `/drift/sync` — provider-aware (uses environment's `gateway_provider`)
- Drift report normalized to business model regardless of provider

---

## 6. Error Codes (Phase 5)

- `GATEWAY_COMPLIANCE_BLOCKED`
- `GATEWAY_RULE_NOT_FOUND`
- `GATEWAY_PROVIDER_NOT_SUPPORTED`
- `GATEWAY_PROVIDER_SWITCH_FORBIDDEN` (e.g. PROD switch without approval)

---

## 7. Control Plane Mapping

Phase 5 extends provider capabilities while keeping shared release semantics from `release-control-plane-blueprint.md`:

- `/governance/rules*` and `/releases/{id}/compliance-check` map to canonical `PolicyDecision` model (`PASS/WARN/BLOCK`)
- provider management endpoints keep provider-specific payload but do not change canonical release lifecycle
- provider-aware publish/drift endpoints map to canonical `ReleaseExecution` and `DriftReport` with environment-scoped adapter resolution
