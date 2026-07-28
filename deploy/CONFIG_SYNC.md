# Docker ↔ K8s 配置同步规范

本地 **Docker Compose（dev）** 与 **Kubernetes（SIT/UAT/PROD）** 使用**同一套环境变量名**注入 Spring / 前端。改 Docker 时须同步 K8s，避免「本地正常、集群异常」。

> **Agent 约定**：凡修改 `deploy/environments/dev/`、`application*.yml` 中的可配置项，在同一变更中按本文 checklist 同步 K8s（见 `.cursor/rules/docker-k8s-config-sync.mdc`）。

---

## 1. 三层写法（新增变量时按顺序）

| 步骤 | 写什么 | 路径 |
|------|--------|------|
| ① 应用绑定 | `${ENV_NAME:默认值}`，禁止 Java 硬编码 | `backend/<服务>/src/main/resources/application.yml`（Docker profile 可写在 `application-docker.yml`） |
| ② Docker | 变量名 + 默认值 | `deploy/environments/dev/.env` **与** `docker-compose.dev.yml` 对应 `service.environment` |
| ③ K8s | **同名** `ENV_NAME` | 见下表「非敏感 / 敏感」 |
| ④ 文档 | 一行说明 | `BUILD_GUIDE.md` §12 环境变量表 |
| ⑤ 仅 Docker | 卷、端口、`depends_on` | **不要**写进 K8s ConfigMap |

---

## 2. K8s 写哪里？

| 类型 | 源文件（按环境分子目录） | 部署脚本 |
|------|--------------------------|----------|
| **非敏感**（URL、开关、TTL、CORS、日志级别等） | `deploy/k8s/config_map/<Environment>/configmap-workflow-platform-config.yml` | `deploy/k8s/ps1/apply-workflow-station-configmap.ps1` |
| **敏感**（密码、JWT、加密密钥、API Key、重置密码等） | `deploy/k8s/secret/<Environment>/secret-workflow-paltform.yml`（`stringData`） | `deploy/k8s/ps1/apply-workflow-station-secret.ps1` |
| **Kong 路由** | `deploy/k8s/config_map/<Environment>/kong-declarative-config.yml` | 随 ConfigMap 或 Istio 脚本一并应用 |
| **Superset 等专项** | `config_map/<Environment>/superset-config.yml` 等 | 按服务清单 |

默认环境目录为 **`preprod`**。其它环境可复制 `config_map/preprod/`、`secret/preprod/` 为 `sit/`、`uat/` 等，部署时传 `-Environment sit`。

后端 Pod（`admin-center`、`user-portal` 等）通过 `envFrom.configMapRef: workflow-platform-config` + `secretRef: workflow-platform-secrets` 注入，**一般不必**改 `deploy/k8s/admin-center.yaml` 等 Deployment，除非新增**仅该服务**的独立 env。

---

## 3. Docker 写哪里？

| 内容 | 路径 |
|------|------|
| 默认值 / 本地密钥（勿提交生产真值） | `deploy/environments/dev/.env` |
| 各服务注入 | `deploy/environments/dev/docker-compose.dev.yml` → 对应 `service` 的 `environment:` |
| 共享变量 | 与 K8s ConfigMap 使用**相同 key**（如 `USER_RESET_PASSWORD`） |

---

## 4. 同步 Checklist（每次改 Docker 配置后勾选）

- [ ] `application.yml`（及需要的 `application-docker.yml`）已用 `${ENV}` 占位
- [ ] `.env` 与 `docker-compose.dev.yml` 已添加/更新同名变量
- [ ] `configmap-workflow-platform-config.yml`（或对应环境目录）已同步**非敏感**项
- [ ] 敏感项已写入 `secret-workflow-paltform.yml`（生产勿留 ConfigMap 明文）
- [ ] `BUILD_GUIDE.md` §12 表已补充（若有新变量）
- [ ] 本地：`docker compose ... up -d --build <service>` 验证
- [ ] K8s：`apply-workflow-station-configmap.ps1` / `secret` + `kubectl rollout restart deployment/<服务>`

---

## 5. 敏感 vs 非敏感（速查）

| 建议 ConfigMap | 建议 Secret |
|----------------|-------------|
| `LOG_LEVEL`、`SWAGGER_ENABLED`、`SPRING_FLYWAY_ENABLED` | `JWT_SECRET`、`ENCRYPTION_SECRET_KEY` |
| `*_URL`、`CORS_ALLOWED_ORIGINS` | `SPRING_DATASOURCE_PASSWORD`、`SPRING_REDIS_PASSWORD` |
| `USER_RESET_PASSWORD`（仅 dev/SIT；生产用 Secret） | `SSO_INTERNAL_TOKEN`、第三方 API Key |
| `SUPERSET_DB_SCHEMA`、`SUPERSET_APP_ROOT`、`SUPERSET_HOST` | `SUPERSET_SECRET_KEY`、`SUPERSET_DATABASE_URI`、`BI_SUPERSET_PASSWORD` |

---

## 6. 相关文档

- 本地构建与 Compose：`BUILD_GUIDE.md`、`deploy/environments/dev/docker-compose.dev.yml` 顶部注释
- K8s 部署：`deploy/k8s/README.md`、`deploy/k8s/ps1/README.md`
- Cursor 部署规则：`.cursor/rules/deployment-infra.mdc`、`.cursor/rules/docker-k8s-config-sync.mdc`
