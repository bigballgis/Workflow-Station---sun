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
| Migrations | **Flyway** per deployable service: `admin-center`, `user-portal`, `developer-workstation` |

The parent `pom.xml` imports the **Spring Cloud BOM** for dependency alignment only. There is **no** Spring Cloud Gateway application in this repo (legacy `api-gateway` module removed).

## Frontend

| Item | Notes |
|------|------|
| Stack | Vue 3 + TypeScript + Vite 5 + Element Plus + Pinia + vue-i18n |
| Apps | `admin-center`, `user-portal`, `developer-workstation`, `login` (unified login shell for K8s `/login/`) |

## Deployment

- Local: `deploy/environments/dev/docker-compose.dev.yml`
- Kubernetes: `deploy/k8s/` — default **excludes** `developer-workstation` for SIT/UAT/PROD; optional YAMLs exist for lab use.
- Edge: Kong (`deploy/kong/`, `deployment-kong.yaml`)

For demo language and seed data conventions, see [demo-data-requirements.md](./demo-data-requirements.md).
