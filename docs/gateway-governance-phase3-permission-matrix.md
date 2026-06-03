# Gateway Governance Phase 3 Permission Matrix

Phase 3 does not introduce new business permissions. Remote (`gateway-mfe`) consumes the same permission keys as Phase 1/2 via shared `@platform/permission` SDK.

## Requirements

- Host passes permission context to remote (JWT claims or shared store)
- Remote must not bypass host RBAC
- Button-level guards in remote mirror Phase 2 matrix

## Host vs Remote Enforcement

| Layer | Enforcement |
|---|---|
| Menu visibility | Host (`AdminLayout.vue`) |
| Route guard | Host router |
| Action buttons | Remote (import shared SDK) |
| API calls | GMS backend (unchanged) |

## Phase 3 Exit (RBAC)

- Remote pages respect same permission keys as embedded mode
- Unauthorized remote routes redirect to host `/403`
