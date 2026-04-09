# Deployment Guide

> 详细构建指南请参考项目根目录的 **BUILD_GUIDE.md**。本文件仅为 deploy/ 目录的快速参考。

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Ingress (K8S) / Nginx                │
│         admin.company.com  portal.company.com           │
│         dev.company.com    n8n.company.com              │
└──────┬──────────────┬──────────────────┬────────────────┘
       │              │                  │
┌──────▼──────┐ ┌─────▼──────┐ ┌────────▼────────┐
│ Admin Center│ │ User Portal│ │ Platform Login  │
│  Frontend   │ │  Frontend  │ │  (nginx /login) │
│  (nginx)    │ │  (nginx)   │ │                 │
└──────┬──────┘ └──┬────┬────┘ └────────┬────────┘
       │           │    │               │
       └───────────┼────┼───────────────┘
                   │    │
            ┌──────▼────▼──────┐
            │   Kong Gateway   │
            │  (API 统一代理)   │
            └──────┬───────────┘
                   │
       ┌───────────┼────┬───────────┐
       │           │    │           │
┌──────▼──────┐ ┌──▼────▼────┐ ┌───▼──────────────┐
│ Admin Center│ │ User Portal│ │ Dev Workstation * │
│  Backend    │ │  Backend   │ │   Backend         │
└──────┬──────┘ └──┬────┬────┘ └───┬──────────────┘
       │           │    │           │
       └───────────┼────┼───────────┘
                   │
            ┌──────▼────▼──────┐
            │ Workflow Engine  │
            │   (Flowable)     │
            └──────┬───────────┘
                   │
    ┌──────────────┼──────────────┐
    │              │              │
┌───▼─────┐  ┌────▼────┐  ┌─────▼────┐
│PostgreSQL│  │  Redis  │  │  Kafka   │
│(公司现有) │  │(K8S部署) │  │(K8S部署) │
└─────────┘  └─────────┘  └──────────┘

* 默认 K8S 清单不部署 developer-workstation；见 optional YAML。
```

## Services（K8S 默认，`deploy.ps1`）

共 **10 个 Deployment**（另含 `pdb.yaml`）：**不含** `developer-workstation`。`deployment-frontend.yaml` 内含 admin-center 与 user-portal **两个**前端 Deployment。

| Service | Type | K8S Manifest | Healthcheck |
|---------|------|-------------|-------------|
| redis | Infrastructure | `deployment-redis.yaml` | redis-cli ping |
| kafka | Infrastructure | `deployment-kafka.yaml` | broker-api-versions |
| n8n | Infrastructure | `deployment-n8n.yaml` | `/healthz` |
| workflow-engine | Backend | `deployment-workflow-engine.yaml` | `/actuator/health` |
| admin-center | Backend | `deployment-admin-center.yaml` | `/api/v1/admin/actuator/health` |
| user-portal | Backend | `deployment-user-portal.yaml` | `/api/portal/actuator/health` |
| kong | Gateway | `deployment-kong.yaml` | `/status` |
| admin-center-frontend | Frontend | `deployment-frontend.yaml` | `/admin/` |
| user-portal-frontend | Frontend | `deployment-frontend.yaml` | `/portal/`（以探针为准） |
| platform-login-frontend | Frontend | `deployment-platform-login-frontend.yaml` | `/login/` |

**可选（勿用于生产租户 unless 政策允许）**：`deployment-developer-workstation-optional.yaml`、`ingress-developer-workstation-optional.yaml`。

镜像构建脚本仍会打 **developer-workstation** 与 **developer-workstation-frontend** 镜像，供本地 Dev Compose 使用。

## NOT Deployed

| Component | Reason |
|-----------|--------|
| PostgreSQL | 使用公司现有数据库 |

## Environments

| Environment | Platform | PostgreSQL | Redis/Kafka/N8N | Config |
|-------------|----------|-----------|-----------------|--------|
| dev | Docker Desktop | 本地容器 | 本地容器 | `environments/dev/` |
| sit | Company K8S | 公司现有 | K8S 自行部署 | `k8s/configmap-sit.yaml` + `secret-sit.yaml` |
| uat | Company K8S | 公司现有 | K8S 自行部署 | `k8s/configmap-uat.yaml` + `secret-uat.yaml` |
| prod | Company K8S | 公司现有 | K8S 自行部署 | `k8s/configmap-prod.yaml` + `secret-prod.yaml` |

## Demo：界面语言与种子数据（英文）

外资银行 Demo 约定：**用户可见的界面与种子数据统一英文**。细则见项目根目录 **`docs/demo-data-requirements.md`**。

- **K8S 前端**（`k8s/deployment-frontend.yaml`）：清单顶部注释已标明；Pod **无** `LOCALE` 类环境变量，默认语言在 **`frontend/*/src/i18n/index.ts`** 构建进静态资源，改语言需改源码并重新构建/推送前端镜像。
- **本地 Compose**：`environments/dev/docker-compose.dev.yml` 文件头有相同说明。
- **完整部署说明**：根目录 **`BUILD_GUIDE.md` §2.5**。

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
# 0. DBA 准备数据库（PostgreSQL 不部署）
#    创建 workflow_platform_{env} 和 n8n_{env} 数据库

# 1. 初始化数据库 Schema（首次部署）
cd deploy/init-scripts
.\init-database.ps1 -DbHost {postgres-host} -DbPort 5432 -DbName workflow_platform_{env} -DbUser platform_{env} -DbPassword {password}

# 2. 更新 K8S 配置
#    deploy/k8s/configmap-{env}.yaml — PostgreSQL 地址、域名
#    deploy/k8s/secret-{env}.yaml — 所有 CHANGE_ME 替换为真实密码

# 3. Build & push images
cd deploy/scripts
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests

# 4. Deploy（Redis + Kafka + N8N + 三后端 + Kong + 三前端 Deployment + login，共 10 个 Deployment；见上文清单）
cd deploy/k8s
.\deploy.ps1 -Environment sit -Tag v1.0.0
```

## Key Rules

1. **PostgreSQL 不部署** — SIT/UAT/PROD 使用公司现有数据库
2. **Redis / Kafka / N8N 自行部署** — K8S 清单已包含
3. **Docker 多阶段构建不可用** — 本地构建 + 复制
4. **前端使用 `Dockerfile.local`** — 不是 `Dockerfile`
5. **前端 `.dockerignore` 不能排除 `dist`**
6. **nginx envsubst 必须显式列出变量** — 见 BUILD_GUIDE.md §6
7. **`.sh`/`.sql` 文件必须 LF 换行** — `.gitattributes` 已配置
8. **环境变量名是 `ENCRYPTION_SECRET_KEY`** — 不是 `ENCRYPTION_KEY`
9. **统一 `*_URL` 命名** — 无 `*_BACKEND_URL` 变量
10. **Kong Gateway 统一代理 API 流量** — 前端 nginx 通过 Kong 访问后端

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
│   ├── deployment-redis.yaml       # Redis (自行部署)
│   ├── deployment-kafka.yaml       # Kafka KRaft (自行部署)
│   ├── deployment-n8n.yaml         # N8N (自行部署)
│   ├── deployment-workflow-engine.yaml
│   ├── deployment-admin-center.yaml
│   ├── deployment-user-portal.yaml
│   ├── deployment-kong.yaml        # Kong Gateway
│   ├── deployment-frontend.yaml    # admin + user-portal 前端
│   ├── deployment-platform-login-frontend.yaml
│   ├── deployment-developer-workstation-optional.yaml  # 勿默认上生产
│   ├── ingress.yaml
│   ├── kustomization.yaml
│   └── deploy.ps1                  # K8S deployment script
├── kong/
│   ├── kong.yml.template           # Kong declarative config template
│   └── docker-entrypoint-kong.sh   # Kong entrypoint (env substitution)
├── scripts/
│   └── build-and-push-k8s.ps1     # Build & push images
├── init-scripts/
│   ├── 00-init-all.sh             # Docker entrypoint (auto-run)
│   ├── init-database.ps1          # Standalone psql init
│   ├── 00-schema/                 # DDL schemas + migrations
│   ├── 01-admin/                  # Admin user + roles
│   ├── 08-digital-lending-v2-en/  # Test function unit
│   ├── 10-simple-approval/        # Simple Approval
│   ├── 12-simple-approval/        # Simple Approval 12
│   ├── 13-procurement-workflow/   # Procurement Workflow
│   └── 14-travel-expense-reimbursement/  # Travel Expense
└── README.md                      # This file
```
