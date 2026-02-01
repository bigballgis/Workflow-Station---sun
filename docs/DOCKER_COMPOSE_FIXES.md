# docker-compose.yml 修改建议

## 🔍 发现的问题

### 1. ⚠️ 缺失关键环境变量

#### workflow-engine 服务
- ❌ 缺少 `JWT_SECRET`
- ❌ 缺少 `ENCRYPTION_SECRET_KEY`
- ❌ 缺少 `SPRING_REDIS_PORT`
- ❌ 缺少 `SPRING_KAFKA_BOOTSTRAP_SERVERS`（如果使用 Kafka）

#### admin-center 服务
- ❌ 缺少 `JWT_SECRET`
- ❌ 缺少 `ENCRYPTION_SECRET_KEY`
- ❌ 缺少 `SPRING_REDIS_PORT`
- ❌ 缺少 `SPRING_KAFKA_BOOTSTRAP_SERVERS`（如果使用 Kafka）

#### user-portal 服务
- ❌ 缺少 `JWT_SECRET`
- ❌ 缺少 `ENCRYPTION_SECRET_KEY`
- ❌ 缺少 `SPRING_REDIS_PORT`
- ❌ 缺少 `WORKFLOW_ENGINE_URL`

#### developer-workstation 服务
- ❌ 缺少 `JWT_SECRET`
- ❌ 缺少 `ENCRYPTION_SECRET_KEY`
- ❌ 缺少 `SPRING_REDIS_PORT`

#### api-gateway 服务
- ❌ 缺少 `JWT_SECRET`
- ❌ 缺少 `WORKFLOW_ENGINE_URL`
- ❌ 缺少 `SPRING_REDIS_PORT`

### 2. ⚠️ 缺少健康检查

以下服务缺少健康检查：
- `workflow-engine`
- `admin-center`
- `user-portal`
- `developer-workstation`
- `api-gateway`

### 3. ⚠️ 缺少重启策略

所有服务都应该添加 `restart` 策略，确保容器异常退出时自动重启。

### 4. ⚠️ 依赖关系不完整

- `api-gateway` 应该依赖 `workflow-engine`
- 前端服务的 `depends_on` 应该使用 `condition: service_started` 或 `service_healthy`

### 5. ⚠️ Kafka 依赖缺失

如果服务使用 Kafka，应该添加对 `kafka` 服务的依赖。

---

## ✅ 建议的修改

### 修改 1: workflow-engine 服务

```yaml
workflow-engine:
  # ... existing config ...
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
    kafka:  # 👈 添加 Kafka 依赖（如果使用）
      condition: service_started
  environment:
    SERVER_PORT: 8080
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/workflow_platform
    SPRING_DATASOURCE_USERNAME: platform
    SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-platform123}
    SPRING_REDIS_HOST: redis
    SPRING_REDIS_PORT: ${SPRING_REDIS_PORT:-6379}  # 👈 添加
    SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD:-redis123}
    SPRING_KAFKA_BOOTSTRAP_SERVERS: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}  # 👈 添加
    ADMIN_CENTER_URL: http://admin-center:8080
    JWT_SECRET: ${JWT_SECRET:-your-256-bit-secret-key-for-development-only}  # 👈 添加
    ENCRYPTION_SECRET_KEY: ${ENCRYPTION_SECRET_KEY:-your-32-byte-aes-256-secret-key!!}  # 👈 添加
  restart: unless-stopped  # 👈 添加
  healthcheck:  # 👈 添加
    test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
```

### 修改 2: admin-center 服务

```yaml
admin-center:
  # ... existing config ...
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
    kafka:  # 👈 添加（如果使用）
      condition: service_started
  environment:
    SERVER_PORT: 8080
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/workflow_platform
    SPRING_DATASOURCE_USERNAME: platform
    SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-platform123}
    SPRING_REDIS_HOST: redis
    SPRING_REDIS_PORT: ${SPRING_REDIS_PORT:-6379}  # 👈 添加
    SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD:-redis123}
    SPRING_KAFKA_BOOTSTRAP_SERVERS: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}  # 👈 添加
    JWT_SECRET: ${JWT_SECRET:-your-256-bit-secret-key-for-development-only}  # 👈 添加
    ENCRYPTION_SECRET_KEY: ${ENCRYPTION_SECRET_KEY:-your-32-byte-aes-256-secret-key!!}  # 👈 添加
  restart: unless-stopped  # 👈 添加
  healthcheck:  # 👈 添加
    test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
```

### 修改 3: user-portal 服务

```yaml
user-portal:
  # ... existing config ...
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
    workflow-engine:
      condition: service_started  # 👈 改为 service_started 或 service_healthy
  environment:
    SERVER_PORT: 8080
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/workflow_platform
    SPRING_DATASOURCE_USERNAME: platform
    SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-platform123}
    SPRING_REDIS_HOST: redis
    SPRING_REDIS_PORT: ${SPRING_REDIS_PORT:-6379}  # 👈 添加
    SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD:-redis123}
    ADMIN_CENTER_URL: http://admin-center:8080
    WORKFLOW_ENGINE_URL: ${WORKFLOW_ENGINE_URL:-http://workflow-engine:8080}  # 👈 添加
    JWT_SECRET: ${JWT_SECRET:-your-256-bit-secret-key-for-development-only}  # 👈 添加
    ENCRYPTION_SECRET_KEY: ${ENCRYPTION_SECRET_KEY:-your-32-byte-aes-256-secret-key!!}  # 👈 添加
  restart: unless-stopped  # 👈 添加
  healthcheck:  # 👈 添加
    test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
```

### 修改 4: developer-workstation 服务

```yaml
developer-workstation:
  # ... existing config ...
  environment:
    SERVER_PORT: 8080
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/workflow_platform
    SPRING_DATASOURCE_USERNAME: platform
    SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-platform123}
    SPRING_REDIS_HOST: redis
    SPRING_REDIS_PORT: ${SPRING_REDIS_PORT:-6379}  # 👈 添加
    SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD:-redis123}
    ADMIN_CENTER_URL: http://admin-center:8080
    JWT_SECRET: ${JWT_SECRET:-your-256-bit-secret-key-for-development-only}  # 👈 添加
    ENCRYPTION_SECRET_KEY: ${ENCRYPTION_SECRET_KEY:-your-32-byte-aes-256-secret-key!!}  # 👈 添加
  restart: unless-stopped  # 👈 添加
  healthcheck:  # 👈 添加
    test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
```

### 修改 5: api-gateway 服务

```yaml
api-gateway:
  # ... existing config ...
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
    workflow-engine:  # 👈 添加
      condition: service_started
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/workflow_platform
    SPRING_DATASOURCE_USERNAME: platform
    SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-platform123}
    SPRING_REDIS_HOST: redis
    SPRING_REDIS_PORT: ${SPRING_REDIS_PORT:-6379}  # 👈 添加
    SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD:-redis123}
    WORKFLOW_ENGINE_URL: ${WORKFLOW_ENGINE_URL:-http://workflow-engine:8080}  # 👈 添加
    ADMIN_CENTER_URL: ${ADMIN_CENTER_URL:-http://admin-center:8080}  # 👈 使用环境变量
    JWT_SECRET: ${JWT_SECRET:-your-256-bit-secret-key-for-development-only}  # 👈 添加
  restart: unless-stopped  # 👈 添加
  healthcheck:  # 👈 添加
    test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
```

### 修改 6: 前端服务依赖关系

```yaml
frontend-admin:
  # ... existing config ...
  depends_on:
    admin-center:
      condition: service_started  # 👈 添加 condition
    workflow-engine:
      condition: service_started  # 👈 添加 condition

frontend-portal:
  # ... existing config ...
  depends_on:
    user-portal:
      condition: service_started  # 👈 添加 condition
    api-gateway:
      condition: service_started  # 👈 添加 condition

frontend-developer:
  # ... existing config ...
  depends_on:
    developer-workstation:
      condition: service_started  # 👈 添加 condition
    workflow-engine:
      condition: service_started  # 👈 添加 condition
```

---

## 📊 优先级

### 🔴 高优先级（必须修复）
1. 添加 `JWT_SECRET` 和 `ENCRYPTION_SECRET_KEY` 到所有后端服务
2. 添加 `SPRING_REDIS_PORT` 到所有使用 Redis 的服务
3. 添加 `WORKFLOW_ENGINE_URL` 到 `api-gateway` 和 `user-portal`

### 🟡 中优先级（建议修复）
1. 添加健康检查到所有后端服务
2. 添加重启策略
3. 完善依赖关系（特别是 `api-gateway` 依赖 `workflow-engine`）

### 🟢 低优先级（可选）
1. 添加 Kafka 依赖（如果服务使用 Kafka）
2. 前端服务的 `depends_on` 使用 `condition`

---

## 🎯 总结

主要问题：
- **环境变量不完整**：缺少 JWT、加密密钥、Redis 端口等
- **缺少健康检查**：无法监控服务状态
- **缺少重启策略**：容器异常退出后不会自动重启
- **依赖关系不完整**：可能导致启动顺序问题

建议按照优先级逐步修复这些问题。
