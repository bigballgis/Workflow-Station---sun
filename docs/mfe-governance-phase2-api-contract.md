# MFE Governance Phase 2 API Contract

Base path: `/api/v1/admin/frontend-modules`

## New Runtime/Ops APIs

- `POST /{id}/health-check`
  - triggers remote availability check
- `GET /{id}/versions`
  - list historical versions for rollback
- `POST /{id}/switch-version`
  - switch active version
- `POST /{id}/rollback-version`
  - rollback to target or previous version

## Example switch request

```json
{
  "version": "1.0.3",
  "remoteEntryUrl": "https://cdn.example.com/notification-mfe/1.0.3/remoteEntry.js"
}
```

## Error Codes

- `MFE_REMOTE_UNHEALTHY`
- `MFE_VERSION_NOT_FOUND`
- `MFE_SWITCH_BLOCKED`

## Control Plane Mapping

This phase contract aligns to `release-control-plane-blueprint.md` as follows:

- `POST /{id}/switch-version` and `POST /{id}/rollback-version` map to canonical release transition semantics
- `GET /{id}/versions` maps to release history/read model for rollback decisions
- `POST /{id}/health-check` maps to pre-publish/runtime health signals used by control-plane ops and policy gates

