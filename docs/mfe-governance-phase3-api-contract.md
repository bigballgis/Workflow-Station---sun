# MFE Governance Phase 3 API Contract

Phase 3 introduces no major new admin APIs.

## Contract Rules

- Keep Phase 1/2 module registry APIs backward compatible
- Add optional metadata fields for workflow-critical module:
  - `warmupRequired` (bool)
  - `preloadPriority` (int)

## Runtime Fields (optional)

```json
{
  "moduleCode": "workflow-mfe",
  "warmupRequired": true,
  "preloadPriority": 1
}
```

## Control Plane Mapping

Phase 3 remains API-compatible with Phase 1/2 and aligns to `release-control-plane-blueprint.md` by contract semantics:

- optional runtime metadata (`warmupRequired`, `preloadPriority`) enriches release execution readiness signals
- workflow-critical module operations use the same canonical lifecycle and audit vocabulary as other domains
- no new mandatory control-plane endpoint is required in this phase

