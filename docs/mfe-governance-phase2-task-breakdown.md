# MFE Governance Phase 2 Task Breakdown

## Milestones

| Milestone | Deliverable |
|---|---|
| M1 | pilot mfe repos scaffolded |
| M2 | CI/CD and versioned remote artifacts |
| M3 | runtime onboarding in host |
| M4 | rollback and resilience validation |

## Tasks

- FE-P1: extract `notifications` to `notification-mfe`
- FE-P2: extract `delegations` to `delegation-mfe`
- FE-H1: host integration for pilot routes
- OPS-1: pipeline for each MFE with version tagging
- OPS-2: CDN/static hosting + cache strategy
- QA-1: e2e (enable/disable/switch-version/rollback)
- SEC-1: permission checks for module toggles in admin

## DoD

- Both pilot MFEs work in DEV/SIT
- Version switch no host redeploy
- Rollback under 5 minutes by config switch

