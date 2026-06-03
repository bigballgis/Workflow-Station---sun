# Gateway Governance Documentation Index

Progressive design for Gateway Governance Domain across Phase 1–5.

Shared release governance contract:

- [Release Control Plane Blueprint](./release-control-plane-blueprint.md)
- [Release Control Plane API Mapping](./release-control-plane-api-mapping.md)

## Phase Overview

| Phase | Focus | Duration (est.) |
|---|---|---|
| [Phase 1](./gateway-governance-phase1-blueprint.md) | Admin Center embedded domain, publish/rollback MVP | 2 weeks |
| [Phase 2](./gateway-governance-phase2-blueprint.md) | GMS extraction, drift, monitoring, promotion | 4 weeks |
| [Phase 3](./gateway-governance-phase3-blueprint.md) | `gateway-mfe` micro frontend | 3 weeks |
| [Phase 4](./gateway-governance-phase4-blueprint.md) | Developer API Marketplace | 4 weeks |
| [Phase 5](./gateway-governance-phase5-blueprint.md) | Multi-gateway governance platform | 6 weeks |

---

## Document Pack by Phase

Each phase includes up to 6 documents:

| Doc Type | Purpose |
|---|---|
| `*-blueprint.md` | Architecture and scope |
| `*-task-breakdown.md` | Executable tasks |
| `*-api-contract.md` | REST API contract |
| `*-ddl.sql` | Database schema |
| `*-permission-matrix.md` | RBAC matrix |
| `*-kickoff-checklist.md` | Go/no-go checklist |

### Phase 1
- [blueprint](./gateway-governance-phase1-blueprint.md)
- [task-breakdown](./gateway-governance-phase1-task-breakdown.md)
- [api-contract](./gateway-governance-phase1-api-contract.md)
- [ddl.sql](./gateway-governance-phase1-ddl.sql)
- [permission-matrix](./gateway-governance-phase1-permission-matrix.md)
- [kickoff-checklist](./gateway-governance-phase1-kickoff-checklist.md)

### Phase 2
- [blueprint](./gateway-governance-phase2-blueprint.md)
- [task-breakdown](./gateway-governance-phase2-task-breakdown.md)
- [api-contract](./gateway-governance-phase2-api-contract.md)
- [ddl.sql](./gateway-governance-phase2-ddl.sql)
- [permission-matrix](./gateway-governance-phase2-permission-matrix.md)
- [kickoff-checklist](./gateway-governance-phase2-kickoff-checklist.md)

### Phase 3
- [blueprint](./gateway-governance-phase3-blueprint.md)
- [task-breakdown](./gateway-governance-phase3-task-breakdown.md)
- [api-contract](./gateway-governance-phase3-api-contract.md)
- [ddl.sql](./gateway-governance-phase3-ddl.sql)
- [permission-matrix](./gateway-governance-phase3-permission-matrix.md)
- [kickoff-checklist](./gateway-governance-phase3-kickoff-checklist.md)

### Phase 4
- [blueprint](./gateway-governance-phase4-blueprint.md)
- [task-breakdown](./gateway-governance-phase4-task-breakdown.md)
- [api-contract](./gateway-governance-phase4-api-contract.md)
- [ddl.sql](./gateway-governance-phase4-ddl.sql)
- [permission-matrix](./gateway-governance-phase4-permission-matrix.md)
- [kickoff-checklist](./gateway-governance-phase4-kickoff-checklist.md)

### Phase 5
- [blueprint](./gateway-governance-phase5-blueprint.md)
- [task-breakdown](./gateway-governance-phase5-task-breakdown.md)
- [api-contract](./gateway-governance-phase5-api-contract.md)
- [ddl.sql](./gateway-governance-phase5-ddl.sql)
- [permission-matrix](./gateway-governance-phase5-permission-matrix.md)
- [kickoff-checklist](./gateway-governance-phase5-kickoff-checklist.md)

---

## Core Principles (All Phases)

1. Kong is Runtime; platform metadata DB is Source of Truth
2. No direct Frontend → Kong Admin API
3. Business model: API / Application / Policy / Release (not Kong native objects)
4. Gateway Domain embedded in Admin Center until Phase 3 MFE extraction
5. Gateway Adapter SPI for multi-provider future (Phase 5)
6. Phase 4+ release semantics align to the shared Release Control Plane contract while preserving Gateway-specific payloads and provider details
