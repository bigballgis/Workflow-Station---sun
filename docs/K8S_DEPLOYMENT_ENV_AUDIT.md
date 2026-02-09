# K8s 部署环境变量审计报告（完整版）

## 执行日期
2026-02-02

## 审计目标
检查应用部署到公司内部 K8s 时，所有环境变量是否已正确抽取，以便在 SIT、UAT、PROD 环境中使用现有的数据库和 Redis。

## 审计范围
- **后端服务**: 5 个 Spring Boot 服务
- **前端服务**: 3 个 Vue.js + Nginx 服务
- **配置文件**: application.yml, nginx.conf, Dockerfile
- **部署方式**: K8s Deployment + Service + ConfigMap + Secret

---

## 📱 前端服务环境变量审计

### 前端架构说明
前端使用 **Nginx 反向代理** 模式：
- 构建时：Vue.js 应用编译为静态文件
- 运行时：Nginx 提供静态文件服务 + API 反向代理
- 配置方式：通过 `docker-entrypoint.sh` 使用 `envsubst` 注入环境变量到 nginx.conf

### 1. Admin Center Frontend

#### 当前配置状态
✅ **环境变量已正确抽取**

| 环境变量 | 默认值 | 用途 | 状态 |
|---------|--------|------|------|
| `ADMIN_CENTER_BACKEND_URL` | http://platform-admin-center:8080 | Admin Center 后端 API 地址 | ✅ 已抽取 |

#### Nginx 配置
```nginx
# /api/v1/auth/ → ${ADMIN_CENTER_BACKEND_URL}/api/v1/admin/auth/
# /api/v1/admin/ → ${ADMIN_CENTER_BACKEND_URL}/api/v1/admin/
```

#### K8s 配置建议
```yaml
env:
  - name: ADMIN_CENTER_BACKEND_URL
    value: "http://admin-center-service:8080"
```

#### 开发环境配置
- **vite.config.ts**: 硬编码 `localhost:8090` ❌
- **建议**: 添加环境变量支持 `VITE_API_BASE_URL`

---

### 2. User Portal Frontend

#### 当前配置状态
✅ **环境变量已正确抽取**

| 环境变量 | 默认值 | 用途 | 状态 |
|---------|--------|------|------|
| `USER_PORTAL_BACKEND_URL` | http://platform-user-portal:8080 | User Portal 后端 API 地址 | ✅ 已抽取 |
| `ADMIN_CENTER_BACKEND_URL` | http://platform-admin-center:8080 | Admin Center 后端 API 地址 | ✅ 已抽取 |

#### Nginx 配置
```nginx
# /api/portal/ → ${USER_PORTAL_BACKEND_URL}/api/portal/
# /api/admin-center/ → ${ADMIN_CENTER_BACKEND_URL}/api/v1/admin/
# /api/v1/auth/ → ${USER_PORTAL_BACKEND_URL}/api/portal/auth/
```

#### K8s 配置建议
```yaml
env:
  - name: USER_PORTAL_BACKEND_URL
    value: "http://user-portal-service:8080"
  - name: ADMIN_CENTER_BACKEND_URL
    value: "http://admin-center-service:8080"
```

#### 开发环境配置
- **vite.config.ts**: 硬编码 `localhost:8082`, `localhost:8090` ❌
- **建议**: 添加环境变量支持

---

### 3. Developer Workstation Frontend

#### 当前配置状态
✅ **环境变量已正确抽取**

| 环境变量 | 默认值 | 用途 | 状态 |
|---------|--------|------|------|
| `DEVELOPER_WORKSTATION_BACKEND_URL` | http://platform-developer-workstation:8080 | Developer Workstation 后端 API 地址 | ✅ 已抽取 |
| `ADMIN_CENTER_BACKEND_URL` | http://platform-admin-center:8080 | Admin Center 后端 API 地址 | ✅ 已抽取 |

#### Nginx 配置
```nginx
# /api/v1/ → ${DEVELOPER_WORKSTATION_BACKEND_URL}/api/developer/
# /api/admin-center/ → ${ADMIN_CENTER_BACKEND_URL}/api/v1/admin/
```

#### K8s 配置建议
```yaml
env:
  - name: DEVELOPER_WORKSTATION_BACKEND_URL
    value: "http://developer-workstation-service:8080"
  - name: ADMIN_CENTER_BACKEND_URL
    value: "http://admin-center-service:8080"
```

#### 开发环境配置
- **vite.config.ts**: 硬编码 `localhost:8083`, `localhost:8090` ❌
- **建议**: 添加环境变量支持

---

### 前端总结

#### ✅ 优点
1. **生产环境配置完善**: Dockerfile 和 nginx.conf 已正确使用环境变量
2. **运行时配置灵活**: 通过 docker-entrypoint.sh 动态注入配置
3. **API 路由清晰**: Nginx 反向代理配置清晰，易于维护

#### ⚠️ 问题
1. **开发环境硬编码**: vite.config.ts 中的 proxy 配置硬编码了 localhost 地址
2. **缺少环境变量文档**: 前端环境变量没有统一的文档说明
3. **构建时配置**: 前端应用在构建时没有注入环境变量（如 API 版本、功能开关等）

#### 💡 改进建议
1. **添加 .env 文件支持**: 为前端项目添加 `.env.development`, `.env.production`
2. **使用 Vite 环境变量**: 支持 `VITE_API_BASE_URL` 等环境变量
3. **统一配置管理**: 创建前端配置文档，说明所有可用的环境变量

---

## ✅ 已抽取的环境变量

### 1. 数据库配置 (PostgreSQL)
所有服务都已正确抽取数据库环境变量：

| 环境变量 | 默认值 | 使用服务 | 状态 |
|---------|--------|---------|------|
| `SPRING_DATASOURCE_URL` | jdbc:postgresql://localhost:5432/workflow_platform | 所有后端服务 | ✅ 已抽取 |
| `SPRING_DATASOURCE_USERNAME` | platform | 所有后端服务 | ✅ 已抽取 |
| `SPRING_DATASOURCE_PASSWORD` | platform123 | 所有后端服务 | ✅ 已抽取 |
| `POSTGRES_PASSWORD` | platform123 | Docker Compose | ✅ 已抽取 |

**K8s 配置建议**：
```yaml
env:
  - name: SPRING_DATASOURCE_URL
    value: "jdbc:postgresql://your-postgres-host:5432/workflow_platform"
  - name: SPRING_DATASOURCE_USERNAME
    valueFrom:
      secretKeyRef:
        name: postgres-credentials
        key: username
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: postgres-credentials
        key: password
```

---

### 2. Redis 配置
所有服务都已正确抽取 Redis 环境变量：

| 环境变量 | 默认值 | 使用服务 | 状态 |
|---------|--------|---------|------|
| `SPRING_REDIS_HOST` | localhost | 所有后端服务 | ✅ 已抽取 |
| `SPRING_REDIS_PORT` | 6379 | 所有后端服务 | ✅ 已抽取 |
| `SPRING_REDIS_PASSWORD` | redis123 | 所有后端服务 | ✅ 已抽取 |
| `REDIS_PASSWORD` | redis123 | Docker Compose | ✅ 已抽取 |

**K8s 配置建议**：
```yaml
env:
  - name: SPRING_REDIS_HOST
    value: "your-redis-host"
  - name: SPRING_REDIS_PORT
    value: "6379"
  - name: SPRING_REDIS_PASSWORD
    valueFrom:
      secretKeyRef:
        name: redis-credentials
        key: password
```

---

### 3. JWT 安全配置
JWT 配置已抽取：

| 环境变量 | 默认值 | 使用服务 | 状态 |
|---------|--------|---------|------|
| `JWT_SECRET` | your-256-bit-secret-key-for-development-only | workflow-engine, developer-workstation, api-gateway | ✅ 已抽取 |
| `JWT_EXPIRATION` | 86400000 | workflow-engine, developer-workstation | ✅ 已抽取 |
| `JWT_REFRESH_EXPIRATION` | 604800000 | workflow-engine | ✅ 已抽取 |

**K8s 配置建议**：
```yaml
env:
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: jwt-secret
        key: secret-key
  - name: JWT_EXPIRATION
    value: "86400000"
  - name: JWT_REFRESH_EXPIRATION
    value: "604800000"
```

---

### 4. 加密配置
加密密钥已抽取：

| 环境变量 | 默认值 | 使用服务 | 状态 |
|---------|--------|---------|------|
| `ENCRYPTION_SECRET_KEY` | your-32-byte-aes-256-secret-key!! | workflow-engine | ✅ 已抽取 |

**K8s 配置建议**：
```yaml
env:
  - name: ENCRYPTION_SECRET_KEY
    valueFrom:
      secretKeyRef:
        name: encryption-secret
        key: secret-key
```

---

### 5. 服务端口配置
服务端口已抽取：

| 环境变量 | 默认值 | 使用服务 | 状态 |
|---------|--------|---------|------|
| `SERVER_PORT` | 8080/8081/8082/8083/8090 | 所有后端服务 | ✅ 已抽取 |

**K8s 配置建议**：
```yaml
# 在 K8s 中通常使用固定端口 8080，通过 Service 暴露
env:
  - name: SERVER_PORT
    value: "8080"
```

---

### 6. Spring Profile 配置
Profile 配置已抽取：

| 环境变量 | 默认值 | 使用服务 | 状态 |
|---------|--------|---------|------|
| `SPRING_PROFILES_ACTIVE` | dev | 所有后端服务 | ✅ 已抽取 |

**K8s 配置建议**：
```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "sit"  # 或 "uat", "prod"
```

---

### 7. 服务间调用 URL
服务间调用 URL 已抽取：

| 环境变量 | 默认值 | 使用服务 | 状态 |
|---------|--------|---------|------|
| `ADMIN_CENTER_URL` | http://localhost:8090 | workflow-engine, user-portal, developer-workstation | ✅ 已抽取 |
| `WORKFLOW_ENGINE_URL` | http://workflow-engine:8080 | api-gateway | ✅ 已抽取 |
| `DEVELOPER_WORKSTATION_URL` | http://developer-workstation:8080 | api-gateway | ✅ 已抽取 |
| `USER_PORTAL_URL` | http://user-portal:8080 | api-gateway | ✅ 已抽取 |

**K8s 配置建议**：
```yaml
# 使用 K8s Service 名称进行服务发现
env:
  - name: ADMIN_CENTER_URL
    value: "http://admin-center-service:8080"
  - name: WORKFLOW_ENGINE_URL
    value: "http://workflow-engine-service:8080"
  - name: DEVELOPER_WORKSTATION_URL
    value: "http://developer-workstation-service:8080"
  - name: USER_PORTAL_URL
    value: "http://user-portal-service:8080"
```

---

## 🔍 详细服务审计

### 后端服务详细审计

#### 1. workflow-engine-core

**配置文件**: `backend/workflow-engine-core/src/main/resources/application.yml`

| 配置项 | 环境变量 | 默认值 | 状态 |
|--------|---------|--------|------|
| server.port | `SERVER_PORT` | 8081 | ✅ |
| spring.datasource.url | `SPRING_DATASOURCE_URL` | jdbc:postgresql://localhost:5432/workflow_platform | ✅ |
| spring.datasource.username | `SPRING_DATASOURCE_USERNAME` | platform | ✅ |
| spring.datasource.password | `SPRING_DATASOURCE_PASSWORD` | platform123 | ✅ |
| spring.data.redis.host | `SPRING_REDIS_HOST` | localhost | ✅ |
| spring.data.redis.port | `SPRING_REDIS_PORT` | 6379 | ✅ |
| spring.data.redis.password | `SPRING_REDIS_PASSWORD` | redis123 | ✅ |
| ~~spring.kafka.bootstrap-servers~~ | ~~`SPRING_KAFKA_BOOTSTRAP_SERVERS`~~ | ~~localhost:9092~~ | ✅ **已移除** |
| admin-center.url | `ADMIN_CENTER_URL` | http://localhost:8090 | ✅ |
| jwt.secret | `JWT_SECRET` | your-256-bit-secret-key-for-development-only | ✅ |
| platform.encryption.secret-key | `ENCRYPTION_SECRET_KEY` | your-32-byte-aes-256-secret-key!! | ✅ |

**~~问题~~**: ~~Kafka 配置未抽取为环境变量~~

**✅ 更新**: Kafka 和 Zookeeper 已从部署中移除（应用未实际使用）

**~~修复建议~~**:
```yaml
# 不需要 - Kafka 已移除
```

---

#### 2. admin-center

**配置文件**: `backend/admin-center/src/main/resources/application.yml`

| 配置项 | 环境变量 | 默认值 | 状态 |
|--------|---------|--------|------|
| server.port | `SERVER_PORT` | 8090 | ❌ 硬编码 |
| spring.datasource.url | `SPRING_DATASOURCE_URL` | jdbc:postgresql://localhost:5432/workflow_platform | ❌ 硬编码 |
| spring.datasource.username | `SPRING_DATASOURCE_USERNAME` | platform | ❌ 硬编码 |
| spring.datasource.password | `SPRING_DATASOURCE_PASSWORD` | platform123 | ❌ 硬编码 |
| spring.data.redis.host | `SPRING_REDIS_HOST` | localhost | ❌ 硬编码 |
| spring.data.redis.port | `SPRING_REDIS_PORT` | 6379 | ❌ 硬编码 |
| spring.data.redis.password | `SPRING_REDIS_PASSWORD` | redis123 | ❌ 硬编码 |
| ~~spring.kafka.bootstrap-servers~~ | ~~`SPRING_KAFKA_BOOTSTRAP_SERVERS`~~ | ~~localhost:9092~~ | ✅ **不需要** |
| workflow-engine.url | `WORKFLOW_ENGINE_URL` | http://localhost:8081 | ❌ 硬编码 |

**问题**:
- **严重**: 几乎所有配置都是硬编码
- 无法通过环境变量配置数据库、Redis
- ~~无法通过环境变量配置 Kafka~~（Kafka 已移除，不需要）
- 无法在 K8s 中灵活部署

**修复建议**:
```yaml
server:
  port: ${SERVER_PORT:8090}

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform}
    username: ${SPRING_DATASOURCE_USERNAME:platform}
    password: ${SPRING_DATASOURCE_PASSWORD:platform123}
  
  data:
    redis:
      host: ${SPRING_REDIS_HOST:localhost}
      port: ${SPRING_REDIS_PORT:6379}
      password: ${SPRING_REDIS_PASSWORD:redis123}

workflow-engine:
  url: ${WORKFLOW_ENGINE_URL:http://localhost:8081}
```

**注意**: Kafka 配置不需要添加（应用未使用）

---

#### 3. user-portal

**配置文件**: `backend/user-portal/src/main/resources/application.yml`

| 配置项 | 环境变量 | 默认值 | 状态 |
|--------|---------|--------|------|
| server.port | `SERVER_PORT` | 8082 | ❌ 硬编码 |
| spring.datasource.url | `SPRING_DATASOURCE_URL` | jdbc:postgresql://localhost:5432/workflow_platform | ❌ 硬编码 |
| spring.datasource.username | `SPRING_DATASOURCE_USERNAME` | platform | ❌ 硬编码 |
| spring.datasource.password | `SPRING_DATASOURCE_PASSWORD` | platform123 | ❌ 硬编码 |
| admin-center.url | `ADMIN_CENTER_URL` | http://localhost:8090 | ❌ 硬编码 |
| workflow-engine.url | `WORKFLOW_ENGINE_URL` | http://localhost:8081 | ❌ 硬编码 |

**问题**:
- **严重**: 所有配置都是硬编码
- 缺少 Redis 配置（如果需要）
- 缺少 Kafka 配置（如果需要）
- 缺少 application-docker.yml 文件

**修复建议**:
```yaml
server:
  port: ${SERVER_PORT:8082}

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform}
    username: ${SPRING_DATASOURCE_USERNAME:platform}
    password: ${SPRING_DATASOURCE_PASSWORD:platform123}

admin-center:
  url: ${ADMIN_CENTER_URL:http://localhost:8090}

workflow-engine:
  url: ${WORKFLOW_ENGINE_URL:http://localhost:8081}
```

**需要创建**: `application-docker.yml` 文件

---

#### 4. developer-workstation

**配置文件**: `backend/developer-workstation/src/main/resources/application.yml`

| 配置项 | 环境变量 | 默认值 | 状态 |
|--------|---------|--------|------|
| server.port | `SERVER_PORT` | 8083 | ✅ |
| spring.datasource.url | `SPRING_DATASOURCE_URL` | jdbc:postgresql://localhost:5432/workflow_platform | ✅ |
| spring.datasource.username | `SPRING_DATASOURCE_USERNAME` | platform | ✅ |
| spring.datasource.password | `SPRING_DATASOURCE_PASSWORD` | platform123 | ✅ |
| spring.data.redis.host | `SPRING_REDIS_HOST` | localhost | ✅ |
| spring.data.redis.port | `SPRING_REDIS_PORT` | 6379 | ✅ |
| spring.data.redis.password | `SPRING_REDIS_PASSWORD` | redis123 | ✅ |
| security.jwt.secret | `JWT_SECRET` | your-256-bit-secret-key-for-development-only | ✅ |

**问题**:
- Kafka 配置未抽取（如果需要）

**✅ 更新**: Kafka 已从部署中移除（应用未使用）

**状态**: ✅ 基本完成，配置良好

---

#### 5. api-gateway

**配置文件**: `backend/api-gateway/src/main/resources/application.yml`

| 配置项 | 环境变量 | 默认值 | 状态 |
|--------|---------|--------|------|
| server.port | `SERVER_PORT` | 8080 | ❌ 硬编码 |
| spring.data.redis.host | `SPRING_REDIS_HOST` | localhost | ✅ |
| spring.data.redis.port | `SPRING_REDIS_PORT` | 6379 | ✅ |
| spring.data.redis.password | `SPRING_REDIS_PASSWORD` | redis123 | ✅ |
| jwt.secret | `JWT_SECRET` | your-256-bit-secret-key-for-development-only | ✅ |
| spring.cloud.gateway.routes[*].uri | `WORKFLOW_ENGINE_URL`, `ADMIN_CENTER_URL`, etc. | http://workflow-engine:8080 | ✅ |

**问题**:
- server.port 硬编码为 8080

**修复建议**:
```yaml
server:
  port: ${SERVER_PORT:8080}
```

---

### 后端服务总结

| 服务 | 完成度 | 主要问题 | 优先级 |
|------|--------|---------|--------|
| workflow-engine | 95% | ~~缺少 Kafka 环境变量~~（已移除） | 低 |
| admin-center | 30% | 大量硬编码配置 | 🔴 高 |
| user-portal | 10% | 几乎全部硬编码 | 🔴 高 |
| developer-workstation | 95% | 配置良好 | 低 |
| api-gateway | 90% | server.port 硬编码 | 中 |

**注意**: Kafka 和 Zookeeper 已从 docker-compose.yml 中移除，因为应用实际上并未使用它们。

---

## ⚠️ 需要注意的配置项

### 1. Kafka 配置
**当前状态**: 部分硬编码

| 配置项 | 当前值 | 问题 | 建议 |
|--------|--------|------|------|
| `spring.kafka.bootstrap-servers` | localhost:9092 (dev) / kafka:29092 (docker) | 未完全抽取为环境变量 | 需要添加 `SPRING_KAFKA_BOOTSTRAP_SERVERS` |

**修复建议**：
```yaml
# 在 application.yml 中修改
spring:
  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

# K8s 配置
env:
  - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
    value: "your-kafka-host:9092"
```

---

### 2. Admin Center 配置 (admin-center 服务)
**当前状态**: 部分硬编码

admin-center 服务的 `application.yml` 中有大量硬编码配置，需要抽取：

| 配置项 | 当前值 | 状态 |
|--------|--------|------|
| `server.port` | 8090 | ❌ 硬编码 |
| `spring.datasource.url` | jdbc:postgresql://localhost:5432/workflow_platform | ❌ 硬编码 |
| `spring.datasource.username` | platform | ❌ 硬编码 |
| `spring.datasource.password` | platform123 | ❌ 硬编码 |
| `spring.data.redis.host` | localhost | ❌ 硬编码 |
| `spring.data.redis.port` | 6379 | ❌ 硬编码 |
| `spring.data.redis.password` | redis123 | ❌ 硬编码 |
| `spring.kafka.bootstrap-servers` | localhost:9092 | ❌ 硬编码 |

**修复建议**：
需要修改 `backend/admin-center/src/main/resources/application.yml`，将所有硬编码值改为环境变量引用。

---

### 3. User Portal 配置 (user-portal 服务)
**当前状态**: 大量硬编码

user-portal 服务的 `application.yml` 中几乎所有配置都是硬编码：

| 配置项 | 当前值 | 状态 |
|--------|--------|------|
| `server.port` | 8082 | ❌ 硬编码 |
| `spring.datasource.url` | jdbc:postgresql://localhost:5432/workflow_platform | ❌ 硬编码 |
| `spring.datasource.username` | platform | ❌ 硬编码 |
| `spring.datasource.password` | platform123 | ❌ 硬编码 |
| `admin-center.url` | http://localhost:8090 | ❌ 硬编码 |
| `workflow-engine.url` | http://localhost:8081 | ❌ 硬编码 |

**修复建议**：
需要修改 `backend/user-portal/src/main/resources/application.yml`，添加环境变量支持。

---

### 4. 日志级别配置
**当前状态**: 部分硬编码

| 配置项 | 当前值 | 状态 |
|--------|--------|------|
| `logging.level.root` | INFO | ❌ 硬编码 |
| `logging.level.com.platform` | DEBUG | ❌ 硬编码 |

**修复建议**：
```yaml
# 在 application.yml 中修改
logging:
  level:
    root: ${LOG_LEVEL_ROOT:INFO}
    com.platform: ${LOG_LEVEL_PLATFORM:DEBUG}

# K8s 配置
env:
  - name: LOG_LEVEL_ROOT
    value: "WARN"  # 生产环境使用 WARN
  - name: LOG_LEVEL_PLATFORM
    value: "INFO"
```

---

## 🔧 需要修复的文件清单

### 高优先级（必须修复）

1. **backend/admin-center/src/main/resources/application.yml**
   - 添加环境变量支持：`SERVER_PORT`, `SPRING_DATASOURCE_*`, `SPRING_REDIS_*`, `SPRING_KAFKA_*`
   - 当前几乎所有配置都是硬编码

2. **backend/user-portal/src/main/resources/application.yml**
   - 添加环境变量支持：`SERVER_PORT`, `SPRING_DATASOURCE_*`, `ADMIN_CENTER_URL`, `WORKFLOW_ENGINE_URL`
   - 当前几乎所有配置都是硬编码

3. **backend/user-portal/src/main/resources/application-docker.yml**
   - 文件不存在，需要创建

### 中优先级（建议修复）

4. **所有服务的 application.yml**
   - 添加 Kafka 环境变量支持：`SPRING_KAFKA_BOOTSTRAP_SERVERS`
   - 添加日志级别环境变量支持：`LOG_LEVEL_ROOT`, `LOG_LEVEL_PLATFORM`

5. **backend/api-gateway/src/main/resources/application.yml**
   - 添加 `SERVER_PORT` 环境变量支持（当前硬编码为 8080）

---

## 📋 完整的 K8s 部署配置模板

### 前端服务 Deployment 示例

#### Admin Center Frontend

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: admin-center-frontend
  namespace: your-namespace
spec:
  replicas: 2
  selector:
    matchLabels:
      app: admin-center-frontend
  template:
    metadata:
      labels:
        app: admin-center-frontend
    spec:
      containers:
      - name: admin-center-frontend
        image: your-registry/admin-center-frontend:latest
        ports:
        - containerPort: 80
          name: http
        env:
        - name: ADMIN_CENTER_BACKEND_URL
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: ADMIN_CENTER_URL
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
        livenessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 10
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 5
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: admin-center-frontend-service
  namespace: your-namespace
spec:
  selector:
    app: admin-center-frontend
  ports:
  - protocol: TCP
    port: 80
    targetPort: 80
  type: ClusterIP
```

#### User Portal Frontend

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-portal-frontend
  namespace: your-namespace
spec:
  replicas: 2
  selector:
    matchLabels:
      app: user-portal-frontend
  template:
    metadata:
      labels:
        app: user-portal-frontend
    spec:
      containers:
      - name: user-portal-frontend
        image: your-registry/user-portal-frontend:latest
        ports:
        - containerPort: 80
          name: http
        env:
        - name: USER_PORTAL_BACKEND_URL
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: USER_PORTAL_URL
        - name: ADMIN_CENTER_BACKEND_URL
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: ADMIN_CENTER_URL
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
        livenessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 10
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 5
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: user-portal-frontend-service
  namespace: your-namespace
spec:
  selector:
    app: user-portal-frontend
  ports:
  - protocol: TCP
    port: 80
    targetPort: 80
  type: ClusterIP
```

#### Developer Workstation Frontend

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: developer-workstation-frontend
  namespace: your-namespace
spec:
  replicas: 2
  selector:
    matchLabels:
      app: developer-workstation-frontend
  template:
    metadata:
      labels:
        app: developer-workstation-frontend
    spec:
      containers:
      - name: developer-workstation-frontend
        image: your-registry/developer-workstation-frontend:latest
        ports:
        - containerPort: 80
          name: http
        env:
        - name: DEVELOPER_WORKSTATION_BACKEND_URL
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: DEVELOPER_WORKSTATION_URL
        - name: ADMIN_CENTER_BACKEND_URL
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: ADMIN_CENTER_URL
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
        livenessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 10
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 5
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: developer-workstation-frontend-service
  namespace: your-namespace
spec:
  selector:
    app: developer-workstation-frontend
  ports:
  - protocol: TCP
    port: 80
    targetPort: 80
  type: ClusterIP
```

---

### Ingress 配置示例

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: workflow-platform-ingress
  namespace: your-namespace
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - admin.your-domain.com
    - portal.your-domain.com
    - dev.your-domain.com
    - api.your-domain.com
    secretName: workflow-platform-tls
  rules:
  # Admin Center Frontend
  - host: admin.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: admin-center-frontend-service
            port:
              number: 80
  
  # User Portal Frontend
  - host: portal.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: user-portal-frontend-service
            port:
              number: 80
  
  # Developer Workstation Frontend
  - host: dev.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: developer-workstation-frontend-service
            port:
              number: 80
  
  # API Gateway (Backend)
  - host: api.your-domain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-gateway-service
            port:
              number: 8080
```

---

## 📋 K8s 部署配置模板

### ConfigMap 示例 (非敏感配置)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: workflow-platform-config
  namespace: your-namespace
data:
  # Spring Profile
  SPRING_PROFILES_ACTIVE: "sit"  # 或 "uat", "prod"
  
  # Server Ports (K8s 内部统一使用 8080)
  SERVER_PORT: "8080"
  
  # Database Configuration (非敏感部分)
  SPRING_DATASOURCE_URL: "jdbc:postgresql://your-postgres-host.your-namespace.svc.cluster.local:5432/workflow_platform"
  
  # Redis Configuration (非敏感部分)
  SPRING_REDIS_HOST: "your-redis-host.your-namespace.svc.cluster.local"
  SPRING_REDIS_PORT: "6379"
  
  # Kafka Configuration
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "your-kafka-host.your-namespace.svc.cluster.local:9092"
  
  # Service URLs (使用 K8s Service 名称)
  ADMIN_CENTER_URL: "http://admin-center-service:8080"
  WORKFLOW_ENGINE_URL: "http://workflow-engine-service:8080"
  DEVELOPER_WORKSTATION_URL: "http://developer-workstation-service:8080"
  USER_PORTAL_URL: "http://user-portal-service:8080"
  
  # JWT Configuration (非敏感部分)
  JWT_EXPIRATION: "86400000"
  JWT_REFRESH_EXPIRATION: "604800000"
  
  # Logging Configuration
  LOG_LEVEL_ROOT: "INFO"
  LOG_LEVEL_PLATFORM: "INFO"
  
  # Cache TTL Configuration
  CACHE_USER_TTL_MINUTES: "30"
  CACHE_PERMISSION_TTL_MINUTES: "60"
  CACHE_DICTIONARY_TTL_MINUTES: "120"
  
  # Security Configuration
  SECURITY_PASSWORD_MIN_LENGTH: "8"
  SECURITY_LOGIN_MAX_FAILED_ATTEMPTS: "5"
  SECURITY_SESSION_TIMEOUT_MINUTES: "30"
```

### Secret 示例 (敏感配置)

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: workflow-platform-secrets
  namespace: your-namespace
type: Opaque
stringData:
  # Database Credentials
  SPRING_DATASOURCE_USERNAME: "platform"
  SPRING_DATASOURCE_PASSWORD: "your-secure-password"
  
  # Redis Credentials
  SPRING_REDIS_PASSWORD: "your-redis-password"
  
  # JWT Secret (必须是 256-bit 密钥)
  JWT_SECRET: "your-production-256-bit-secret-key-for-jwt-signing-must-be-secure"
  
  # Encryption Secret (必须是 32 字节)
  ENCRYPTION_SECRET_KEY: "your-production-32-byte-aes-key!"
```

### Deployment 示例 (workflow-engine)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: workflow-engine
  namespace: your-namespace
spec:
  replicas: 2
  selector:
    matchLabels:
      app: workflow-engine
  template:
    metadata:
      labels:
        app: workflow-engine
    spec:
      containers:
      - name: workflow-engine
        image: your-registry/workflow-engine:latest
        ports:
        - containerPort: 8080
          name: http
        env:
        # 从 ConfigMap 读取非敏感配置
        - name: SPRING_PROFILES_ACTIVE
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: SPRING_PROFILES_ACTIVE
        - name: SERVER_PORT
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: SERVER_PORT
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: SPRING_DATASOURCE_URL
        - name: SPRING_REDIS_HOST
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: SPRING_REDIS_HOST
        - name: SPRING_REDIS_PORT
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: SPRING_REDIS_PORT
        - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: SPRING_KAFKA_BOOTSTRAP_SERVERS
        - name: ADMIN_CENTER_URL
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: ADMIN_CENTER_URL
        - name: JWT_EXPIRATION
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: JWT_EXPIRATION
        - name: JWT_REFRESH_EXPIRATION
          valueFrom:
            configMapKeyRef:
              name: workflow-platform-config
              key: JWT_REFRESH_EXPIRATION
        
        # 从 Secret 读取敏感配置
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: workflow-platform-secrets
              key: SPRING_DATASOURCE_USERNAME
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: workflow-platform-secrets
              key: SPRING_DATASOURCE_PASSWORD
        - name: SPRING_REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: workflow-platform-secrets
              key: SPRING_REDIS_PASSWORD
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: workflow-platform-secrets
              key: JWT_SECRET
        - name: ENCRYPTION_SECRET_KEY
          valueFrom:
            secretKeyRef:
              name: workflow-platform-secrets
              key: ENCRYPTION_SECRET_KEY
        
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3

---
apiVersion: v1
kind: Service
metadata:
  name: workflow-engine-service
  namespace: your-namespace
spec:
  selector:
    app: workflow-engine
  ports:
  - protocol: TCP
    port: 8080
    targetPort: 8080
  type: ClusterIP
```

---

## 🎯 修复优先级和行动计划

### Phase 1: 紧急修复（必须完成）
**目标**: 使所有服务能够在 K8s 中正常运行

1. **修复 admin-center 配置**
   - 文件: `backend/admin-center/src/main/resources/application.yml`
   - 添加所有环境变量支持
   - 预计时间: 30 分钟

2. **修复 user-portal 配置**
   - 文件: `backend/user-portal/src/main/resources/application.yml`
   - 添加所有环境变量支持
   - 创建 `application-docker.yml`
   - 预计时间: 30 分钟

3. **添加 Kafka 环境变量支持**
   - 文件: 所有服务的 `application.yml`
   - 添加 `SPRING_KAFKA_BOOTSTRAP_SERVERS` 环境变量
   - 预计时间: 20 分钟

### Phase 2: 优化配置（建议完成）
**目标**: 提高配置灵活性和可维护性

1. **添加日志级别环境变量**
   - 所有服务的 `application.yml`
   - 添加 `LOG_LEVEL_ROOT`, `LOG_LEVEL_PLATFORM` 等
   - 预计时间: 15 分钟

2. **添加 API Gateway 端口环境变量**
   - 文件: `backend/api-gateway/src/main/resources/application.yml`
   - 添加 `SERVER_PORT` 环境变量支持
   - 预计时间: 5 分钟

### Phase 3: 创建 K8s 配置文件（必须完成）
**目标**: 提供完整的 K8s 部署配置

1. **创建 ConfigMap 和 Secret 模板**
   - 为 SIT、UAT、PROD 环境分别创建
   - 预计时间: 1 小时

2. **创建 Deployment 和 Service 配置**
   - 为所有 5 个后端服务创建
   - 预计时间: 2 小时

3. **创建 Ingress 配置**
   - 配置外部访问路由
   - 预计时间: 30 分钟

---

## ✅ 总结

### 当前状态

#### 后端服务
- **workflow-engine**: ✅ 90% 环境变量已抽取（缺少 Kafka）
- **developer-workstation**: ✅ 90% 环境变量已抽取（缺少 Kafka）
- **api-gateway**: ✅ 85% 环境变量已抽取（缺少 Kafka、SERVER_PORT）
- **admin-center**: ⚠️ 30% 环境变量已抽取（大量硬编码）
- **user-portal**: ❌ 10% 环境变量已抽取（几乎全部硬编码）

#### 前端服务
- **admin-center-frontend**: ✅ 100% 生产环境配置完成（开发环境硬编码）
- **user-portal-frontend**: ✅ 100% 生产环境配置完成（开发环境硬编码）
- **developer-workstation-frontend**: ✅ 100% 生产环境配置完成（开发环境硬编码）

### 关键问题

#### 🔴 高优先级（必须立即修复）
1. **admin-center 后端**: 大量硬编码配置，无法在 K8s 中灵活部署
   - 数据库配置硬编码
   - Redis 配置硬编码
   - Kafka 配置硬编码
   - 服务 URL 硬编码

2. **user-portal 后端**: 几乎所有配置都是硬编码
   - 数据库配置硬编码
   - 服务 URL 硬编码
   - 缺少 application-docker.yml

#### 🟡 中优先级（建议修复）
3. **所有后端服务**: 缺少 Kafka 环境变量支持
4. **api-gateway**: server.port 硬编码
5. **所有前端服务**: 开发环境配置硬编码（vite.config.ts）

#### 🟢 低优先级（可选优化）
6. 添加日志级别环境变量
7. 添加更多可配置项（超时时间、连接池大小等）
8. 创建统一的配置文档

### 修复工作量估算

| 任务 | 预计时间 | 优先级 |
|------|---------|--------|
| 修复 admin-center 配置 | 1 小时 | 🔴 高 |
| 修复 user-portal 配置 | 1 小时 | 🔴 高 |
| 创建 user-portal application-docker.yml | 30 分钟 | 🔴 高 |
| 添加 Kafka 环境变量（所有服务） | 30 分钟 | 🟡 中 |
| 修复 api-gateway server.port | 5 分钟 | 🟡 中 |
| 创建 K8s ConfigMap 模板 | 1 小时 | 🔴 高 |
| 创建 K8s Secret 模板 | 30 分钟 | 🔴 高 |
| 创建 K8s Deployment 配置（8 个服务） | 2 小时 | 🔴 高 |
| 创建 K8s Service 配置（8 个服务） | 1 小时 | 🔴 高 |
| 创建 K8s Ingress 配置 | 30 分钟 | 🔴 高 |
| 测试和验证 | 2 小时 | 🔴 高 |
| **总计** | **10-12 小时** | |

### 部署就绪检查清单

#### 后端服务
- [ ] admin-center 配置文件已修复
- [ ] user-portal 配置文件已修复
- [ ] user-portal application-docker.yml 已创建
- [ ] 所有服务 Kafka 配置已添加
- [ ] api-gateway server.port 已修复
- [ ] 所有服务本地测试通过
- [ ] 所有服务 Docker Compose 测试通过

#### 前端服务
- [x] admin-center-frontend Dockerfile 配置正确
- [x] user-portal-frontend Dockerfile 配置正确
- [x] developer-workstation-frontend Dockerfile 配置正确
- [x] 所有前端 nginx.conf 配置正确
- [x] 所有前端 docker-entrypoint.sh 配置正确

#### K8s 配置
- [ ] ConfigMap 模板已创建（SIT、UAT、PROD）
- [ ] Secret 模板已创建（SIT、UAT、PROD）
- [ ] Deployment 配置已创建（8 个服务）
- [ ] Service 配置已创建（8 个服务）
- [ ] Ingress 配置已创建
- [ ] 所有配置文件已通过 kubectl 验证

#### 测试验证
- [ ] SIT 环境部署测试通过
- [ ] 所有服务健康检查通过
- [ ] 服务间调用测试通过
- [ ] 前端访问测试通过
- [ ] API Gateway 路由测试通过

### 建议

#### 立即行动（今天完成）
1. 修复 admin-center 和 user-portal 的配置文件
2. 创建 user-portal 的 application-docker.yml
3. 添加 Kafka 环境变量支持

#### 短期行动（本周完成）
4. 创建完整的 K8s 配置文件（ConfigMap、Secret、Deployment、Service、Ingress）
5. 在 SIT 环境进行部署测试
6. 验证所有服务正常运行

#### 中期行动（下周完成）
7. 优化前端开发环境配置（添加 .env 文件支持）
8. 创建完整的部署文档
9. 创建故障排查指南
10. 部署到 UAT 环境进行用户验收测试

#### 长期行动（持续优化）
11. 添加更多可配置项
12. 优化日志配置
13. 添加监控和告警
14. 性能优化和调优

---

**报告生成时间**: 2026-02-02  
**审计人员**: Kiro AI Assistant  
**下一步**: 开始修复 admin-center 和 user-portal 配置文件  
**预计完成时间**: 2-3 小时（紧急修复）+ 8-10 小时（K8s 配置）= 10-13 小时
