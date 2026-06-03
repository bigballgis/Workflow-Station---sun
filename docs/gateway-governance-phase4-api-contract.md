# Gateway Governance Phase 4 API Contract

Base path: `/api/v1/admin/gateway` (GMS) + Developer Workstation catalog endpoints

---

## 1. API Catalog (Developer-facing)

### List Published APIs

- **GET** `/catalog/apis?environmentCode=DEV&domain=order&page=0&size=20`

```json
{
  "items": [
    {
      "apiId": 1001,
      "apiCode": "order-query-api",
      "name": "Order Query API",
      "version": "v1",
      "basePath": "/api/orders",
      "visibility": "INTERNAL"
    }
  ]
}
```

### Get API Catalog Detail (with OpenAPI)

- **GET** `/catalog/apis/{apiId}/versions/{version}`

---

## 2. Subscription Request

### Create Subscription Request

- **POST** `/subscriptions/request`

```json
{
  "applicationId": 5001,
  "environmentCode": "DEV",
  "apiVersionIds": [2001, 2002],
  "justification": "Portal needs order read access"
}
```

### List My Subscription Requests

- **GET** `/subscriptions/requests?status=PENDING`

### Get Request Detail

- **GET** `/subscriptions/requests/{requestId}`

---

## 3. Subscription Approval (Admin / Approver)

### List Pending Approvals

- **GET** `/subscriptions/approvals?status=PENDING`

### Approve / Reject

- **POST** `/subscriptions/requests/{requestId}/decide`

```json
{
  "approved": true,
  "comment": "Approved for DEV"
}
```

On approve: GMS auto-provisions access policy + credential and optionally publishes.

---

## 4. Active Subscriptions

### List Application Subscriptions

- **GET** `/applications/{appId}/subscriptions`

### Revoke Subscription

- **DELETE** `/subscriptions/{subscriptionId}`

---

## 5. Marketplace Admin

### Set Catalog Visibility

- **PUT** `/catalog/apis/{apiId}/visibility`

```json
{
  "visibility": "INTERNAL",
  "visibleInMarketplace": true,
  "allowedEnvironments": ["DEV", "SIT"]
}
```

---

## 6. Workflow Callback (Internal)

- **POST** `/internal/subscriptions/workflow-callback`

Called by workflow-engine on process completion. Not exposed to public clients.

---

## 7. Error Codes (Phase 4)

- `GATEWAY_SUBSCRIPTION_NOT_FOUND`
- `GATEWAY_SUBSCRIPTION_ALREADY_EXISTS`
- `GATEWAY_CATALOG_NOT_VISIBLE`
- `GATEWAY_APPROVAL_PENDING`
- `GATEWAY_PROVISION_FAILED`

---

## 8. Control Plane Mapping

This API set maps to shared release governance semantics in `release-control-plane-blueprint.md`:

- subscription request/approval endpoints map to canonical `ApprovalTicket` and decision events
- auto-provision on approval maps to `release.publish.*` execution events in control-plane taxonomy
- marketplace governance operations remain Gateway-domain payloads while using shared lifecycle/policy/audit vocabulary
