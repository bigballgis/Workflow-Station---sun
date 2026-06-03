# Gateway Governance Phase 5 Kickoff Checklist

Status: **BACKEND COMPLETE** (2026-05-28) | Frontend + E2E: pending

## Prerequisites

- [x] Phase 4 exit criteria met (marketplace backend operational)
- [x] GatewayProvider SPI stable from Phase 1-2

## Gate Checklist

### G1 - Scope
- [x] Kong + APISIX adapters in scope; Envoy basic support
- [x] Traefik stub only (optional — omitted)
- [x] Governance rules center + compliance engine in scope
- [x] No forced migration of existing Kong PROD to other providers

### G2 - Architecture
- [x] Per-environment provider selection approved *(GatewayProviderFactory by environment.gateway_provider)*
- [x] Rules engine expression format approved *(heuristic-based for MVP; full expression evaluator deferred)*
- [x] BLOCK vs WARN severity behavior approved
- [x] Cross-provider drift normalization approved *(DriftDetectorService uses providerFactory.resolve(env))*
- [x] Control-plane mapping reviewed and approved (`release-control-plane-blueprint.md`, `release-control-plane-api-mapping.md`)

### G3 - Cross-Team
- [x] Backend: APISIX/Envoy adapter owners assigned *(implemented as stubs)*
- [ ] Security: PROD provider switch approval policy *(dual-approval recommended, not yet enforced)*
- [ ] Compliance: export format and retention approved
- [ ] QA: multi-provider test environments ready

## Implementation Status (2026-05-28)

### Backend — COMPLETE
| Deliverable | Status | Files |
|---|---|---|
| Entities (GovernanceRule, ComplianceCheck, ProviderRevision) | Done | 3 entities; GovernanceRule uses @EntityListeners |
| Repositories | Done | 3 JPA repos; env-code + enabled queries for rules |
| GatewayProviderFactory | Done | Resolves KONG/APISIX/ENVOY by environment.gateway_provider |
| ApisixGatewayProvider | Done | Implements GatewayProvider SPI — route/upstream/plugin mapping |
| EnvoyGatewayProvider | Done | Implements GatewayProvider SPI — xDS stub |
| Kong + Stub ConditionalOnProperty removed | Done | All 4 adapters coexist for Phase 5 multi-provider |
| GovernanceRuleService | Done | CRUD + list by env/enabled |
| ComplianceService | Done | 5 rule types (NAMING, SECURITY, VERSIONING, TRAFFIC, ENVIRONMENT); BLOCK/WARN |
| ReleaseService (multi-provider upgrade) | Done | providerFactory.resolve(env) + ProviderRevision tracking |
| DriftDetectorService (multi-provider upgrade) | Done | providerFactory.resolve(env).fetchRuntimeState(env) |
| GovernanceController | Done | GET/POST/PUT/DELETE /gateway/governance/rules |
| ComplianceController | Done | POST/GET /gateway/releases/{id}/compliance-check |
| ProviderController | Done | GET /gateway/providers, PUT env provider, GET revisions |
| DDL (43-gateway-governance-phase5.sql) | Done | 3 tables + CHECK constraint on severity |
| Permissions | Done | 6 new keys + role bindings for GATEWAY_ADMIN, SECURITY_AUDITOR |
| Admin-center mirror | Done | All Phase 5 files synced; admin-center compiles |

### Frontend — PENDING
| Deliverable | Status |
|---|---|
| Rules Center UI (gateway-mfe: /gateway/governance/rules) | Not started |
| Provider Config UI (environment provider selector) | Not started |
| Compliance Dashboard (release detail: block/warn results) | Not started |

### E2E Testing — PENDING
| Deliverable | Status |
|---|---|
| Multi-provider publish (same API to Kong + APISIX) | Not started |
| Governance rules block invalid PROD publishes | Not started |
| Compliance report export | Not started |

## Go/No-Go

- [x] **GO** *(backend)* / **NO-GO** *(frontend + E2E)*

## Sign-off

| Role | Owner | Sign-off |
|---|---|---|
| Platform Architect | | |
| Backend Lead | qiweige | 2026-05-28 |
| Security Reviewer | | |
| Compliance Officer | | |
