# 环境变量更新文档

**日期**: 2026-02-02  
**状态**: ✅ 完成

## 概述

为了支持前端 Nginx 代理正确转发请求到后端服务，添加了前端后端 URL 环境变量配置。

---

## 更新内容

### 1. DEV 环境 (.env 文件)

**文件**: `deploy/environments/dev/.env`

#### 新增环境变量

```bash
# Backend Service URLs (for backend-to-backend communication)
ADMIN_CENTER_URL=http://localhost:8090
WORKFLOW_ENGINE_URL=http://localhost:8081
USER_PORTAL_URL=http://localhost:8082
DEVELOPER_WORKSTATION_URL=http://localhost:8083
API_GATEWAY_URL=http://localhost:8080

# Frontend Backend URLs (for nginx proxy configuration in Docker)
# These are used by frontend containers to proxy API requests to backend services
ADMIN_CENTER_BACKEND_URL=http://platform-admin-center-dev:8080
USER_PORTAL_BACKEND_URL=http://platform-user-portal-dev:8080
DEVELOPER_WORKSTATION_BACKEND_URL=http://platform-developer-workstation-dev:8080
WORKFLOW_ENGINE_BACKEND_URL=http://platform-workflow-engine-dev:8080
API_GATEWAY_BACKEND_URL=http://platform-api-gateway-dev:8080
```

#### 说明

- **Backend Service URLs**: 用于后端服务之间的相互调用（使用 localhost）
- **Frontend Backend URLs**: 用于前端 Nginx 容器代理请求到后端服务（使用 Docker 容器名称）

### 2. K8s 环境 (ConfigMap 文件)

更新了以下文件：
- `deploy/k8s/configmap-sit.yaml`
- `deploy/k8s/configmap-uat.yaml`
- `deploy/k8s/configmap-prod.yaml`

#### 新增配置

```yaml
# 服务间调用 URL（使用 K8s Service 名称）
ADMIN_CENTER_URL: "http://admin-center-service:8080"
WORKFLOW_ENGINE_URL: "http://workflow-engine-service:8080"
DEVELOPER_WORKSTATION_URL: "http://developer-workstation-service:8080"
USER_PORTAL_URL: "http://user-portal-service:8080"
API_GATEWAY_URL: "http://api-gateway-service:8080"

# 前端 Nginx 代理后端 URL（K8s 内部服务名称）
ADMIN_CENTER_BACKEND_URL: "http://admin-center-service:8080"
USER_PORTAL_BACKEND_URL: "http://user-portal-service:8080"
DEVELOPER_WORKSTATION_BACKEND_URL: "http://developer-workstation-service:8080"
WORKFLOW_ENGINE_BACKEND_URL: "http://workflow-engine-service:8080"
API_GATEWAY_BACKEND_URL: "http://api-gateway-service:8080"
```

---

## 环境变量用途说明

### Backend Service URLs

这些 URL 用于**后端服务之间的相互调用**：

| 变量名 | DEV 环境值 | K8s 环境值 | 用途 |
|--------|-----------|-----------|------|
| `ADMIN_CENTER_URL` | `http://localhost:8090` | `http://admin-center-service:8080` | Admin Center 服务地址 |
| `WORKFLOW_ENGINE_URL` | `http://localhost:8081` | `http://workflow-engine-service:8080` | Workflow Engine 服务地址 |
| `USER_PORTAL_URL` | `http://localhost:8082` | `http://user-portal-service:8080` | User Portal 服务地址 |
| `DEVELOPER_WORKSTATION_URL` | `http://localhost:8083` | `http://developer-workstation-service:8080` | Developer Workstation 服务地址 |
| `API_GATEWAY_URL` | `http://localhost:8080` | `http://api-gateway-service:8080` | API Gateway 服务地址 |

**使用场景**:
- User Portal 调用 Admin Center 获取用户信息
- User Portal 调用 Workflow Engine 处理流程
- Developer Workstation 调用 Admin Center 获取权限信息

### Frontend Backend URLs

这些 URL 用于**前端 Nginx 代理转发请求到后端服务**：

| 变量名 | DEV 环境值 | K8s 环境值 | 用途 |
|--------|-----------|-----------|------|
| `ADMIN_CENTER_BACKEND_URL` | `http://platform-admin-center-dev:8080` | `http://admin-center-service:8080` | Admin Center 前端代理地址 |
| `USER_PORTAL_BACKEND_URL` | `http://platform-user-portal-dev:8080` | `http://user-portal-service:8080` | User Portal 前端代理地址 |
| `DEVELOPER_WORKSTATION_BACKEND_URL` | `http://platform-developer-workstation-dev:8080` | `http://developer-workstation-service:8080` | Developer Workstation 前端代理地址 |
| `WORKFLOW_ENGINE_BACKEND_URL` | `http://platform-workflow-engine-dev:8080` | `http://workflow-engine-service:8080` | Workflow Engine 前端代理地址 |
| `API_GATEWAY_BACKEND_URL` | `http://platform-api-gateway-dev:8080` | `http://api-gateway-service:8080` | API Gateway 前端代理地址 |

**使用场景**:
- 前端 Nginx 配置中的 `proxy_pass` 指令
- 前端容器需要访问后端 API 时的代理转发

---

## Nginx 配置示例

### Admin Center Frontend

```nginx
location /api/v1/admin/ {
    proxy_pass ${ADMIN_CENTER_BACKEND_URL}/api/v1/admin/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

### User Portal Frontend

```nginx
location /api/portal/ {
    proxy_pass ${USER_PORTAL_BACKEND_URL}/api/portal/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

location /api/admin-center/ {
    proxy_pass ${ADMIN_CENTER_BACKEND_URL}/api/v1/admin/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

### Developer Workstation Frontend

```nginx
location /api/v1/ {
    proxy_pass ${DEVELOPER_WORKSTATION_BACKEND_URL};
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

---

## Docker Compose 配置示例

### Admin Center Frontend

```yaml
admin-center-frontend:
  image: dev-admin-center-frontend
  container_name: platform-admin-center-frontend-dev
  ports:
    - "${ADMIN_CENTER_FRONTEND_PORT}:80"
  environment:
    ADMIN_CENTER_BACKEND_URL: http://platform-admin-center-dev:8080
  networks:
    - platform-dev-network
```

### User Portal Frontend

```yaml
user-portal-frontend:
  image: dev-user-portal-frontend
  container_name: platform-user-portal-frontend-dev
  ports:
    - "${USER_PORTAL_FRONTEND_PORT}:80"
  environment:
    USER_PORTAL_BACKEND_URL: http://platform-user-portal-dev:8080
    ADMIN_CENTER_BACKEND_URL: http://platform-admin-center-dev:8080
  networks:
    - platform-dev-network
```

### Developer Workstation Frontend

```yaml
developer-workstation-frontend:
  image: dev-developer-workstation-frontend
  container_name: platform-developer-workstation-frontend-dev
  ports:
    - "${DEVELOPER_WORKSTATION_FRONTEND_PORT}:80"
  environment:
    DEVELOPER_WORKSTATION_BACKEND_URL: http://platform-developer-workstation-dev:8080
    ADMIN_CENTER_BACKEND_URL: http://platform-admin-center-dev:8080
  networks:
    - platform-dev-network
```

---

## K8s Deployment 配置示例

### Frontend Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: admin-center-frontend
  namespace: workflow-platform-sit
spec:
  template:
    spec:
      containers:
      - name: admin-center-frontend
        image: your-registry/admin-center-frontend:latest
        env:
        - name: ADMIN_CENTER_BACKEND_URL
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: ADMIN_CENTER_BACKEND_URL
```

---

## 环境对比

| 环境 | Backend Service URL 格式 | Frontend Backend URL 格式 |
|------|-------------------------|--------------------------|
| **DEV (本地)** | `http://localhost:{port}` | `http://platform-{service}-dev:8080` |
| **DEV (Docker)** | `http://{service}:8080` | `http://platform-{service}-dev:8080` |
| **SIT/UAT/PROD (K8s)** | `http://{service}-service:8080` | `http://{service}-service:8080` |

---

## 使用指南

### 1. DEV 环境部署

```bash
# 1. 确保 .env 文件已更新
cat deploy/environments/dev/.env | grep BACKEND_URL

# 2. 重启 Docker Compose 服务
cd deploy/environments/dev
docker-compose -f docker-compose.dev.yml down
docker-compose -f docker-compose.dev.yml up -d

# 3. 验证环境变量
docker exec platform-admin-center-frontend-dev env | grep BACKEND_URL
```

### 2. K8s 环境部署

```bash
# 1. 应用更新的 ConfigMap
kubectl apply -f deploy/k8s/configmap-sit.yaml

# 2. 重启前端 Pod 使配置生效
kubectl rollout restart deployment/admin-center-frontend -n workflow-platform-sit
kubectl rollout restart deployment/user-portal-frontend -n workflow-platform-sit
kubectl rollout restart deployment/developer-workstation-frontend -n workflow-platform-sit

# 3. 验证环境变量
kubectl exec -it deployment/admin-center-frontend -n workflow-platform-sit -- env | grep BACKEND_URL
```

---

## 故障排查

### 问题 1: 前端无法访问后端 API

**症状**: 前端请求返回 502 Bad Gateway 或 Connection Refused

**排查步骤**:

1. 检查环境变量是否正确注入
   ```bash
   # Docker
   docker exec <frontend-container> env | grep BACKEND_URL
   
   # K8s
   kubectl exec <frontend-pod> -n <namespace> -- env | grep BACKEND_URL
   ```

2. 检查后端服务是否运行
   ```bash
   # Docker
   docker ps | grep <backend-service>
   
   # K8s
   kubectl get pods -n <namespace> | grep <backend-service>
   ```

3. 检查网络连通性
   ```bash
   # Docker
   docker exec <frontend-container> ping <backend-container>
   
   # K8s
   kubectl exec <frontend-pod> -n <namespace> -- curl http://<backend-service>:8080/actuator/health
   ```

### 问题 2: Nginx 代理配置未生效

**症状**: 环境变量已设置，但 Nginx 仍使用旧的配置

**解决方法**:

1. 检查 Nginx 配置文件是否使用环境变量
   ```nginx
   # 正确的配置
   proxy_pass ${ADMIN_CENTER_BACKEND_URL}/api/v1/admin/;
   
   # 错误的配置（硬编码）
   proxy_pass http://localhost:8090/api/v1/admin/;
   ```

2. 重新构建前端镜像
   ```bash
   docker build -t dev-admin-center-frontend ./frontend/admin-center
   ```

3. 重启前端容器
   ```bash
   docker-compose -f docker-compose.dev.yml restart admin-center-frontend
   ```

### 问题 3: K8s 环境变量未更新

**症状**: 更新了 ConfigMap 但 Pod 仍使用旧值

**解决方法**:

1. 确认 ConfigMap 已更新
   ```bash
   kubectl get configmap workflow-platform-config -n <namespace> -o yaml
   ```

2. 重启 Pod
   ```bash
   kubectl rollout restart deployment/<deployment-name> -n <namespace>
   ```

3. 验证新值
   ```bash
   kubectl exec <pod-name> -n <namespace> -- env | grep BACKEND_URL
   ```

---

## 最佳实践

### 1. 环境变量命名规范

- ✅ 使用大写字母和下划线: `ADMIN_CENTER_BACKEND_URL`
- ✅ 使用描述性名称: `BACKEND_URL` 而不是 `URL`
- ✅ 区分用途: `_URL` (服务间调用) vs `_BACKEND_URL` (前端代理)

### 2. URL 格式规范

- ✅ 包含协议: `http://` 或 `https://`
- ✅ 不包含尾部斜杠: `http://service:8080` 而不是 `http://service:8080/`
- ✅ 使用服务名称而不是 IP 地址

### 3. 配置管理

- ✅ 使用环境变量而不是硬编码
- ✅ 在 `.env` 文件中集中管理
- ✅ 为不同环境使用不同的值
- ✅ 在 ConfigMap 中管理 K8s 配置

### 4. 安全考虑

- ✅ 不要在环境变量中存储敏感信息（使用 Secret）
- ✅ 限制环境变量的访问权限
- ✅ 定期审查和更新配置

---

## 相关文档

- [环境配置指南](ENVIRONMENT_CONFIGURATION_GUIDE.md)
- [Spring Profiles 说明](SPRING_PROFILES_EXPLANATION.md)
- [K8s 部署指南](../deploy/k8s/README-DEPLOYMENT.md)
- [Docker Compose 配置](../deploy/environments/dev/docker-compose.dev.yml)

---

## 更新历史

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|----------|------|
| 2026-02-02 | 1.0 | 初始版本，添加前端后端 URL 环境变量 | Kiro |

---

## 总结

✅ **已完成**:
- 在 DEV 环境 `.env` 文件中添加前端后端 URL 环境变量
- 在 K8s ConfigMap 文件中添加前端后端 URL 配置
- 区分了后端服务间调用 URL 和前端代理 URL
- 提供了详细的使用指南和故障排查方法

✅ **配置特点**:
- 环境变量命名清晰，易于理解
- 支持 Docker 和 K8s 两种部署方式
- 配置集中管理，易于维护
- 提供了完整的文档和示例

📚 **下一步**:
- 更新前端 Nginx 配置文件使用这些环境变量
- 测试前端代理功能是否正常
- 在 K8s 环境中验证配置
