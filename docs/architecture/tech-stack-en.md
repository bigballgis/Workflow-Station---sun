# Tech Stack (English)

This mirrors the root `README.md` for linking from English-only runbooks.

## Backend

| Item | Version / Notes |
|------|-----------------|
| Runtime | Java 17 |
| Framework | Spring Boot 3.2 |
| Workflow | Flowable 7 |
| Persistence | Spring Data JPA, PostgreSQL 16.x |
| Cache | Spring Data Redis, Redis 7.2 |
| Messaging | Spring Kafka, Kafka 7.5 (KRaft) |
| Security | JWT, BCrypt; **Kong** at the edge for routing/plugins |
| Schema | **`deploy/init-scripts/00-schema/` is the single source of truth** (Flyway was retired in 2026-06; legacy migrations archived under `docs/legacy-flyway-migrations/`, see [schema-and-migration.md](../schema-and-migration.md)) |

The parent `pom.xml` contains **no Spring Cloud** (BOM and dependencies fully removed — zero code references). There is **no** Spring Cloud Gateway application in this repo (legacy `api-gateway` module removed).

## Testing

- Backend: JUnit 5, jqwik (property-based testing)
- Frontend: Vitest, fast-check (per each app's `package.json`)

## Frontend

| Item | Notes |
|------|------|
| Stack | Vue 3 + TypeScript + Vite 5 + Element Plus + Pinia + vue-i18n |
| Apps | `admin-center`, `user-portal`, `developer-workstation`, `login` (unified login shell for K8s `/login/`) |

## Deployment

- Local: `deploy/environments/dev/docker-compose.dev.yml`
- Kubernetes: `deploy/k8s/` — default **excludes** `developer-workstation` for SIT/UAT/PROD; optional YAMLs exist for lab use.
- Edge: Kong (`deploy/kong/`, `deployment-kong.yaml`)

For demo language and seed data conventions, see [demo-data-requirements.md](../demo-data-requirements.md).
