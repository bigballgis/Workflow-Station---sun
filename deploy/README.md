# Deployment Guide

> 详细构建指南请参考项目根目录的 **BUILD_GUIDE.md**。本文件仅为 deploy/ 目录的快速参考。

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Ingress (K8S) / Nginx                │
│         admin.company.com  portal.company.com           │
│         dev.company.com                                 │
└──────┬──────────────┬──────────────────┬────────────────┘
       │              │                  │
┌──────▼──────┐ ┌─────▼──────┐ ┌────────▼────────┐
│ Admin Center│ │ User Portal│ │ Dev Workstation  │
│  Frontend   │ │  Frontend  │ │   Frontend       │
│  (nginx)    │ │  (nginx)   │ │   (nginx)        │
└──────┬──────┘ └──┬────┬────┘ └────┬─────────────┘
       │           │    │           │
┌──────▼──────┐ ┌──▼────▼────┐ ┌───▼──────────────┐
│ Admin Center│ │ User Portal│ │ Dev Workstation   │
│  Backend    │ │  Backend   │ │   Backend         │
└──────┬──────┘ └──┬────┬────┘ └───┬──────────────┘
       │           │    │           │
       └───────────┼────┼───────────┘
                   │    │
            ┌──────▼────▼──────┐
            │ Workflow Engine  │
            │   (Flowable)     │
            └──────┬───────────┘
                   │
         ┌─────────┴─────────┐
         │                   │
    ┌────▼────┐        ┌─────▼────┐
    │PostgreSQL│        │  Redis   │
    └─────────┘        └──────────┘
```

## Services (7 deployable)

| Service | Type | Image Name | Healthcheck Path |
|---------|------|------------|-----------------|
| workflow-engine | Backend | `workflow-engine-core` | `/actuator/health` |
| admin-center | Backend | `admin-center` | `/api/v1/admin/actuator/health` |
| user-portal | Backend | `user-portal` | `/api/portal/actuator/health` |
| developer-workstation | Backend | `developer-workstation` | `/api/v1/actuator/health` |
| admin-center-frontend | Frontend | `admin-center-frontend` | `/` |
| user-portal-frontend | Frontend | `user-portal-frontend` | `/` |
| developer-workstation-frontend | Frontend | `developer-workstation-frontend` | `/` |

## NOT Deployed

| Component | Reason |
|-----------|--------|
| API Gateway | Bypassed — frontends proxy directly to backends via nginx |
| Kafka / Zookeeper | Not used — workflow-engine simulates via Redis |

## Environments

| Environment | Platform | Infrastructure | Config |
|-------------|----------|----------------|--------|
| dev | Docker Desktop | PG + Redis containers | `environments/dev/` |
| sit | Company K8S | Company-managed | `k8s/configmap-sit.yaml` + `secret-sit.yaml` |
| uat | Company K8S | Company-managed | `k8s/configmap-uat.yaml` + `secret-uat.yaml` |
| prod | Company K8S | Company-managed | `k8s/configmap-prod.yaml` + `secret-prod.yaml` |

## Quick Start

### Dev (Local Docker Desktop)

```powershell
cd deploy/environments/dev
.\build-and-deploy.ps1              # Full build & deploy
.\build-and-deploy.ps1 -SkipMaven   # Skip Maven, rebuild Docker only
.\build-and-deploy.ps1 -SkipFrontend # Backend only
.\build-and-deploy.ps1 -ServicesOnly # Just restart containers
.\build-and-deploy.ps1 -Clean       # Destroy volumes & rebuild
```

### SIT / UAT / PROD (Company K8S)

```powershell
# 1. Build & push images
cd deploy/scripts
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests

# 2. Update configmap/secret with real values
#    deploy/k8s/configmap-{env}.yaml — DB host, Redis host
#    deploy/k8s/secret-{env}.yaml — passwords, JWT secret, encryption key

# 3. Deploy
cd deploy/k8s
.\deploy.ps1 -Environment sit -Tag v1.0.0
```

## Key Rules

1. **Docker multi-stage builds NOT used** — local build + copy only
2. **Frontend uses `Dockerfile.local`** (not `Dockerfile`)
3. **Frontend `.dockerignore` must NOT exclude `dist`**
4. **nginx envsubst must list variables explicitly** — see BUILD_GUIDE.md §5
5. **`.sh`/`.sql` files must use LF line endings** — `.gitattributes` enforces this
6. **Env var name is `ENCRYPTION_SECRET_KEY`** (not `ENCRYPTION_KEY`)
7. **Unified `*_URL` naming** — no `*_BACKEND_URL` variables

## File Structure

```
deploy/
├── environments/
│   ├── dev/
│   │   ├── .env                    # Dev environment variables
│   │   ├── docker-compose.dev.yml  # Local Docker Compose
│   │   └── build-and-deploy.ps1    # One-click dev deploy
│   ├── sit/.env                    # SIT reference config
│   ├── uat/.env                    # UAT reference config
│   └── prod/.env                   # PROD reference config
├── k8s/
│   ├── configmap-{sit,uat,prod}.yaml
│   ├── secret-{sit,uat,prod}.yaml
│   ├── deployment-workflow-engine.yaml
│   ├── deployment-admin-center.yaml
│   ├── deployment-user-portal.yaml
│   ├── deployment-developer-workstation.yaml
│   ├── deployment-frontend.yaml
│   ├── ingress.yaml
│   ├── kustomization.yaml
│   └── deploy.ps1                  # K8S deployment script
├── scripts/
│   └── build-and-push-k8s.ps1     # Build & push images
├── init-scripts/
│   ├── 00-init-all.sh             # Docker entrypoint (auto-run)
│   ├── init-database.ps1          # Standalone psql init
│   ├── 00-schema/                 # DDL schemas + migrations
│   ├── 01-admin/                  # Admin user + roles
│   └── 08-digital-lending-v2-en/  # Test function unit data
└── README.md                      # This file
```
