# Gateway Governance Phase 4 Kickoff Checklist

Status: **BACKEND COMPLETE** (2026-05-28) | Frontend: pending

## Prerequisites

- [x] Phase 3 exit criteria met (gateway-mfe operational)
- [ ] workflow-engine available for approval integration *(stub — manual approve/deny only)*

## Gate Checklist

### G1 - Scope
- [x] Marketplace catalog + subscription + approval in scope
- [x] Design-to-runtime bridge stub only (full auto-register out of scope)
- [x] Billing/monetization out of scope

### G2 - Architecture
- [x] Catalog API visibility model approved
- [ ] Workflow callback integration pattern approved *(stub — no workflow-engine integration yet)*
- [x] Auto-provision flow (approve -> policy + credential) approved
- [ ] Developer Workstation UI entry points defined *(pending frontend)*
- [x] Control-plane mapping reviewed and approved (`release-control-plane-blueprint.md`, `release-control-plane-api-mapping.md`)

### G3 - Cross-Team
- [ ] Product: marketplace IA and approval UX approved
- [x] Backend: subscription entities and workflow callback *(entities + repos + services + controllers done)*
- [ ] Frontend DW: catalog + request UI
- [ ] Frontend UP/Admin: approval UI
- [x] Security: developer vs approver permissions (`phase4-permission-matrix.md`)

## Implementation Status (2026-05-28)

### Backend — COMPLETE
| Deliverable | Status | Files |
|---|---|---|
| Entities (ApiSubscription, SubscriptionRequest, CatalogVisibility) | Done | 3 entities with JSONB + @Builder.Default |
| Repositories | Done | 3 JPA repos with tenant-scoped queries |
| CatalogService | Done | listPublishedApis with domain filter, setVisibility, getVisibility |
| SubscriptionService | Done | create → approve → auto-provision (credential + policy + subscription) |
| CatalogController | Done | GET /gateway/catalog/apis, GET /gateway/catalog/apis/{id}, PUT visibility |
| SubscriptionController | Done | POST request, GET requests, POST decide, DELETE revoke |
| DDL (42-gateway-governance-phase4.sql) | Done | 3 tables + version column on subscription_request |
| Permissions | Done | 6 new keys + role bindings for GATEWAY_ADMIN, SECURITY_AUDITOR |
| AdminAuditAspect | Done | CatalogController + SubscriptionController pointcuts |
| Admin-center mirror | Done | All Phase 4 files synced; admin-center compiles |

### Frontend — PENDING
| Deliverable | Status |
|---|---|
| Developer Workstation: API Catalog page | Not started |
| Developer Workstation: subscription request form | Not started |
| Developer Workstation: My Subscriptions page | Not started |
| User Portal: approval queue | Not started |
| gateway-mfe: catalog visibility management | Not started |

## Go/No-Go

- [x] **GO** *(backend)* / **NO-GO** *(frontend)*

## Sign-off

| Role | Owner | Sign-off |
|---|---|---|
| Product Owner | | |
| Backend Lead | qiweige | 2026-05-28 |
| Frontend Lead (DW) | | |
| Security Reviewer | | |
