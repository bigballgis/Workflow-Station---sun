# Deployment Guide

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

## Services (7 total)

| Service | Type | Port | Description |
|---------|------|------|-------------|
| workflow-engine | Backend | 8080 | Flowable BPMN engine |
| admin-center | Backend | 8080 | Admin management API |
| user-portal | Backend | 8080 | End-user portal API |
| developer-workstation | Backend | 8080 | Developer tools API |
| admin-center-frontend | Frontend | 80 | Admin UI (Vue3) |
| user-portal-frontend | Frontend | 80 | User UI (Vue3) |
| developer-workstation-frontend | Frontend | 80 | Developer UI (Vue3) |

## NOT Deployed

| Component | Reason |
|-----------|--------|
| API Gateway | Bypassed — frontends proxy directly to backends via nginx |
| Kafka / Zookeeper | Not used at runtime — workflow-engine simulates via Redis |

## Environments

| Environment | Platform | Infrastructure | Config Location |
|-------------|----------|----------------|-----------------|
| dev | Docker Desktop (local) | PG + Redis as containers | `environments/dev/` |
| sit | Company K8S | Company-managed PG + Redis | `k8s/configmap-sit.yaml` |
| uat | Company K8S | Company-managed PG + Redis | `k8s/configmap-uat.yaml` |
| prod | Company K8S | Company-managed PG + Redis | `k8s/configmap-prod.yaml` |

## Quick Start

### Dev (Local Docker Desktop)

```powershell
# Full build & deploy
cd deploy/environments/dev
.\build-and-deploy.ps1

# Skip Maven (just restart containers)
.\build-and-deploy.ps1 -SkipMaven

# Clean reset (destroy volumes)
.\build-and-deploy.ps1 -Clean
```

### SIT / UAT / PROD (Company K8S)

```powershell
# 1. Build & push images to registry
cd deploy/scripts
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests

# 2. Update configmap/secret with real values
#    Edit: deploy/k8s/configmap-sit.yaml (DB host, Redis host)
#    Edit: deploy/k8s/secret-sit.yaml (passwords, JWT secret)

# 3. Deploy to K8S
cd deploy/k8s
.\deploy.ps1 -Environment sit -Tag v1.0.0

# Verify
kubectl get pods -n workflow-platform-sit
kubectl get svc -n workflow-platform-sit
kubectl get ingress -n workflow-platform-sit
```

## File Structure

```
deploy/
├── environments/
│   ├── dev/
│   │   ├── .env                    # Dev environment variables
│   │   ├── docker-compose.dev.yml  # Local Docker Compose
│   │   └── build-and-deploy.ps1    # One-click dev deploy
│   ├── sit/.env                    # SIT reference config (K8S)
│   ├── uat/.env                    # UAT reference config (K8S)
│   └── prod/.env                   # Prod reference config (K8S)
├── k8s/
│   ├── configmap-{sit,uat,prod}.yaml
│   ├── secret-{sit,uat,prod}.yaml
│   ├── deployment-workflow-engine.yaml
│   ├── deployment-admin-center.yaml
│   ├── deployment-user-portal.yaml
│   ├── deployment-developer-workstation.yaml
│   ├── deployment-frontend.yaml
│   ├── ingress.yaml
│   └── deploy.ps1                  # K8S deployment script
├── scripts/
│   └── build-and-push-k8s.ps1     # Build & push images
└── init-scripts/                   # Database initialization SQL
```
