# K8s 环境变量完整清单

## 文档说明
本文档列出了所有服务在 K8s 部署时需要的环境变量，包括必需变量和可选变量。

---

## 🔐 敏感配置（存储在 K8s Secret 中）

### 数据库凭证
| 环境变量 | 说明 | 示例值 | 使用服务 |
|---------|------|--------|---------|
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | `platform` | 所有后端服务 |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | `your-secure-password` | 所有后端服务 |

### Redis 凭证
| 环境变量 | 说明 | 示例值 | 使用服务 |
|---------|------|--------|---------|
| `SPRING_REDIS_PASSWORD` | Redis 密码 | `your-redis-password` | 所有后端服务 |

### JWT 密钥
| 环境变量 | 说明 | 示例值 | 使用服务 |
|---------|------|--------|---------|
| `JWT_SECRET` | JWT 签名密钥（256-bit） | `your-production-256-bit-secret-key` | workflow-engine, developer-workstation, api-gateway |

### 加密密钥
| 环境变量 | 说明 | 示例值 | 使用服务 |
|---------|------|--------|---------|
| `ENCRYPTION_SECRET_KEY` | 数据加密密钥（32 字节） | `your-production-32-byte-aes-key!` | workflow-engine |

---

## 📝 非敏感配置（存储在 K8s ConfigMap 中）

### 基础配置

#### Spring Profile
| 环境变量 | 说明 | 默认值 | SIT | UAT | PROD | 使用服务 |
|---------|------|--------|-----|-----|------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring 激活的 Profile | `dev` | `sit` | `uat` | `prod` | 所有后端服务 |

#### 服务端口
| 环境变量 | 说明 | 默认值 | K8s 建议值 | 使用服务 |
|---------|------|--------|-----------|---------|
| `SERVER_PORT` | 服务监听端口 | 8080/8081/8082/8083/8090 | `8080` | 所有后端服务 |

### 数据库配置

| 环境变量 | 说明 | 开发环境 | K8s 环境 | 使用服务 |
|---------|------|---------|---------|---------|
| `SPRING_DATASOURCE_URL` | 数据库连接 URL | `jdbc:postgresql://localhost:5432/workflow_platform` | `jdbc:postgresql://your-postgres-host:5432/workflow_platform` | 所有后端服务 |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | 数据库驱动 | `org.postgresql.Driver` | `org.postgresql.Driver` | 所有后端服务 |

### Redis 配置

| 环境变量 | 说明 | 开发环境 | K8s 环境 | 使用服务 |
|---------|------|---------|---------|---------|
| `SPRING_REDIS_HOST` | Redis 主机地址 | `localhost` | `your-redis-host` | 所有后端服务 |
| `SPRING_REDIS_PORT` | Redis 端口 | `6379` | `6379` | 所有后端服务 |
| `SPRING_REDIS_DATABASE` | Redis 数据库编号 | `0-5` | `0-5` | 各服务不同 |

### ~~Kafka 配置~~（已移除 - 不需要）

**说明**: 应用代码中虽然配置了 Kafka，但实际业务逻辑并未使用。已从 docker-compose.yml 中移除 Zookeeper 和 Kafka。

~~| 环境变量 | 说明 | 开发环境 | K8s 环境 | 使用服务 |~~
~~|---------|------|---------|---------|---------|~~
~~| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka 服务器地址 | `localhost:9092` | `your-kafka-host:9092` | workflow-engine, admin-center, developer-workstation |~~

### 服务间调用 URL

| 环境变量 | 说明 | 开发环境 | K8s 环境 | 使用服务 |
|---------|------|---------|---------|---------|
| `ADMIN_CENTER_URL` | Admin Center 服务 URL | `http://localhost:8090` | `http://admin-center-service:8080` | workflow-engine, user-portal, developer-workstation |
| `WORKFLOW_ENGINE_URL` | Workflow Engine 服务 URL | `http://localhost:8081` | `http://workflow-engine-service:8080` | api-gateway, user-portal, admin-center |
| `DEVELOPER_WORKSTATION_URL` | Developer Workstation 服务 URL | `http://localhost:8083` | `http://developer-workstation-service:8080` | api-gateway |
| `USER_PORTAL_URL` | User Portal 服务 URL | `http://localhost:8082` | `http://user-portal-service:8080` | api-gateway |

### JWT 配置（非敏感部分）

| 环境变量 | 说明 | 默认值 | 建议值 | 使用服务 |
|---------|------|--------|--------|---------|
| `JWT_EXPIRATION` | JWT 过期时间（毫秒） | `86400000` (24小时) | `86400000` | workflow-engine, developer-workstation |
| `JWT_REFRESH_EXPIRATION` | JWT 刷新令牌过期时间（毫秒） | `604800000` (7天) | `604800000` | workflow-engine |

### 日志配置

| 环境变量 | 说明 | 开发环境 | SIT/UAT | PROD | 使用服务 |
|---------|------|---------|---------|------|---------|
| `LOG_LEVEL_ROOT` | 根日志级别 | `INFO` | `INFO` | `WARN` | 所有后端服务 |
| `LOG_LEVEL_PLATFORM` | 平台日志级别 | `DEBUG` | `INFO` | `INFO` | 所有后端服务 |
| `LOG_LEVEL_SQL` | SQL 日志级别 | `DEBUG` | `WARN` | `WARN` | 所有后端服务 |

### 缓存 TTL 配置

| 环境变量 | 说明 | 默认值 | 使用服务 |
|---------|------|--------|---------|
| `CACHE_USER_TTL_MINUTES` | 用户缓存过期时间（分钟） | `30` | admin-center, workflow-engine |
| `CACHE_PERMISSION_TTL_MINUTES` | 权限缓存过期时间（分钟） | `60` | admin-center, workflow-engine |
| `CACHE_DICTIONARY_TTL_MINUTES` | 字典缓存过期时间（分钟） | `120` | admin-center, workflow-engine |

### 安全配置

| 环境变量 | 说明 | 默认值 | 使用服务 |
|---------|------|--------|---------|
| `SECURITY_PASSWORD_MIN_LENGTH` | 密码最小长度 | `8` | admin-center, workflow-engine |
| `SECURITY_LOGIN_MAX_FAILED_ATTEMPTS` | 最大登录失败次数 | `5` | admin-center, workflow-engine |
| `SECURITY_SESSION_TIMEOUT_MINUTES` | 会话超时时间（分钟） | `30` | admin-center, workflow-engine |

---

## 🎨 前端服务环境变量

### Admin Center Frontend

| 环境变量 | 说明 | 开发环境 | K8s 环境 |
|---------|------|---------|---------|
| `ADMIN_CENTER_BACKEND_URL` | Admin Center 后端 API 地址 | `http://localhost:8090` | `http://admin-center-service:8080` |

### User Portal Frontend

| 环境变量 | 说明 | 开发环境 | K8s 环境 |
|---------|------|---------|---------|
| `USER_PORTAL_BACKEND_URL` | User Portal 后端 API 地址 | `http://localhost:8082` | `http://user-portal-service:8080` |
| `ADMIN_CENTER_BACKEND_URL` | Admin Center 后端 API 地址 | `http://localhost:8090` | `http://admin-center-service:8080` |

### Developer Workstation Frontend

| 环境变量 | 说明 | 开发环境 | K8s 环境 |
|---------|------|---------|---------|
| `DEVELOPER_WORKSTATION_BACKEND_URL` | Developer Workstation 后端 API 地址 | `http://localhost:8083` | `http://developer-workstation-service:8080` |
| `ADMIN_CENTER_BACKEND_URL` | Admin Center 后端 API 地址 | `http://localhost:8090` | `http://admin-center-service:8080` |

---

## 📊 按服务分类的环境变量清单

### workflow-engine

**必需变量**:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`
- `SPRING_REDIS_PASSWORD`
- `JWT_SECRET`
- `ENCRYPTION_SECRET_KEY`

**可选变量**:
- `SERVER_PORT` (默认: 8080)
- `SPRING_PROFILES_ACTIVE` (默认: dev)
- ~~`SPRING_KAFKA_BOOTSTRAP_SERVERS`~~ (不需要 - Kafka 已移除)
- `ADMIN_CENTER_URL` (默认: http://localhost:8090)
- `JWT_EXPIRATION` (默认: 86400000)
- `JWT_REFRESH_EXPIRATION` (默认: 604800000)

**注意**: Kafka 配置已移除，应用未实际使用

---

### admin-center

**必需变量**:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`
- `SPRING_REDIS_PASSWORD`

**可选变量**:
- `SERVER_PORT` (默认: 8090)
- `SPRING_PROFILES_ACTIVE` (默认: dev)
- ~~`SPRING_KAFKA_BOOTSTRAP_SERVERS`~~ (不需要 - Kafka 已移除)
- `WORKFLOW_ENGINE_URL` (默认: http://localhost:8081)

---

### user-portal

**必需变量**:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

**可选变量**:
- `SERVER_PORT` (默认: 8082)
- `SPRING_PROFILES_ACTIVE` (默认: dev)
- `ADMIN_CENTER_URL` (默认: http://localhost:8090)
- `WORKFLOW_ENGINE_URL` (默认: http://localhost:8081)

---

### developer-workstation

**必需变量**:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`
- `SPRING_REDIS_PASSWORD`
- `JWT_SECRET`

**可选变量**:
- `SERVER_PORT` (默认: 8083)
- `SPRING_PROFILES_ACTIVE` (默认: dev)
- `JWT_EXPIRATION` (默认: 86400000)

---

### api-gateway

**必需变量**:
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`
- `SPRING_REDIS_PASSWORD`
- `JWT_SECRET`

**可选变量**:
- `SERVER_PORT` (默认: 8080)
- `SPRING_PROFILES_ACTIVE` (默认: dev)
- `WORKFLOW_ENGINE_URL` (默认: http://workflow-engine:8080)
- `ADMIN_CENTER_URL` (默认: http://admin-center:8080)
- `DEVELOPER_WORKSTATION_URL` (默认: http://developer-workstation:8080)
- `USER_PORTAL_URL` (默认: http://user-portal:8080)

---

## 🔍 环境变量验证清单

### 部署前检查

#### SIT 环境
- [ ] 数据库连接信息已配置
- [ ] Redis 连接信息已配置
- [ ] ~~Kafka 连接信息已配置~~（不需要 - Kafka 已移除）
- [ ] 所有服务 URL 已配置为 K8s Service 名称
- [ ] JWT 密钥已生成并配置
- [ ] 加密密钥已生成并配置
- [ ] 日志级别设置为 INFO
- [ ] ConfigMap 已创建
- [ ] Secret 已创建
- [x] Zookeeper 和 Kafka 已从 docker-compose.yml 中移除

#### UAT 环境
- [ ] 数据库连接信息已配置（UAT 数据库）
- [ ] Redis 连接信息已配置（UAT Redis）
- [ ] ~~Kafka 连接信息已配置~~（不需要 - Kafka 已移除）
- [ ] 所有服务 URL 已配置
- [ ] JWT 密钥已更新（不同于 SIT）
- [ ] 加密密钥已更新（不同于 SIT）
- [ ] 日志级别设置为 INFO
- [ ] ConfigMap 已创建
- [ ] Secret 已创建

#### PROD 环境
- [ ] 数据库连接信息已配置（生产数据库）
- [ ] Redis 连接信息已配置（生产 Redis）
- [ ] ~~Kafka 连接信息已配置~~（不需要 - Kafka 已移除）
- [ ] 所有服务 URL 已配置
- [ ] JWT 密钥已更新（强密钥）
- [ ] 加密密钥已更新（强密钥）
- [ ] 日志级别设置为 WARN
- [ ] ConfigMap 已创建
- [ ] Secret 已创建
- [ ] 所有敏感信息已加密存储

---

## 📝 环境变量命名规范

### Spring Boot 规范
- 使用大写字母和下划线：`SPRING_DATASOURCE_URL`
- 嵌套属性使用下划线分隔：`SPRING_DATA_REDIS_HOST`
- 数组使用下标：`SPRING_CLOUD_GATEWAY_ROUTES_0_URI`

### 自定义配置规范
- 使用大写字母和下划线：`JWT_SECRET`
- 前缀表示模块：`CACHE_USER_TTL_MINUTES`
- 后缀表示单位：`_MINUTES`, `_SECONDS`, `_MS`

---

## 🔒 安全最佳实践

### 密钥生成
```bash
# 生成 JWT 密钥（256-bit）
openssl rand -base64 32

# 生成加密密钥（32 字节）
openssl rand -base64 32 | cut -c1-32
```

### Secret 创建
```bash
# 从文件创建 Secret
kubectl create secret generic workflow-platform-secrets \
  --from-literal=SPRING_DATASOURCE_PASSWORD=your-password \
  --from-literal=SPRING_REDIS_PASSWORD=your-redis-password \
  --from-literal=JWT_SECRET=your-jwt-secret \
  --from-literal=ENCRYPTION_SECRET_KEY=your-encryption-key \
  --namespace=your-namespace

# 从 .env 文件创建 Secret
kubectl create secret generic workflow-platform-secrets \
  --from-env-file=.env.prod \
  --namespace=your-namespace
```

### ConfigMap 创建
```bash
# 从文件创建 ConfigMap
kubectl create configmap workflow-platform-config \
  --from-literal=SPRING_PROFILES_ACTIVE=prod \
  --from-literal=SPRING_DATASOURCE_URL=jdbc:postgresql://your-host:5432/workflow_platform \
  --from-literal=SPRING_REDIS_HOST=your-redis-host \
  --namespace=your-namespace

# 从 .env 文件创建 ConfigMap
kubectl create configmap workflow-platform-config \
  --from-env-file=.env.config \
  --namespace=your-namespace
```

---

## 📚 参考文档

- [Spring Boot 外部化配置](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Kubernetes ConfigMap](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [Kubernetes Secret](https://kubernetes.io/docs/concepts/configuration/secret/)
- [12-Factor App 配置](https://12factor.net/config)

---

**文档版本**: 1.0  
**创建日期**: 2026-02-02  
**最后更新**: 2026-02-02  
**维护人员**: DevOps Team
