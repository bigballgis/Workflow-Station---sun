# 环境变量使用检查报告

## 📋 检查结果概览

| 服务 | 状态 | 问题数量 | 详情 |
|------|------|---------|------|
| workflow-engine-core | ✅ 正确 | 0 | 所有配置都使用环境变量 |
| api-gateway | ✅ 正确 | 0 | 所有配置都使用环境变量 |
| admin-center | ❌ 需要修复 | 5 | 数据库、Redis、Kafka 都硬编码 |
| user-portal | ❌ 需要修复 | 4 | 数据库、服务 URL 都硬编码 |
| developer-workstation | ⚠️ 部分正确 | 2 | 数据库配置硬编码 |
| 前端服务 | ✅ 正确 | 0 | 通过 nginx 代理，使用服务名 |

---

## 🔍 详细问题分析

### 1. ✅ workflow-engine-core - 正确

**文件**: `backend/workflow-engine-core/src/main/resources/application.yml`

✅ **正确使用环境变量**:
- `SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:...}`
- `SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME:...}`
- `SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD:...}`
- `SPRING_REDIS_HOST: ${SPRING_REDIS_HOST:...}`
- `SPRING_REDIS_PORT: ${SPRING_REDIS_PORT:...}`
- `SPRING_REDIS_PASSWORD: ${SPRING_REDIS_PASSWORD:...}`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:...}`
- `JWT_SECRET: ${JWT_SECRET:...}`
- `ENCRYPTION_SECRET_KEY: ${ENCRYPTION_SECRET_KEY:...}`
- `ADMIN_CENTER_URL: ${ADMIN_CENTER_URL:...}`

---

### 2. ❌ admin-center - 需要修复

**文件**: `backend/admin-center/src/main/resources/application.yml`

❌ **问题 1**: 数据库配置硬编码
```yaml
datasource:
  url: jdbc:postgresql://localhost:5432/workflow_platform  # ❌ 硬编码
  username: platform  # ❌ 硬编码
  password: platform123  # ❌ 硬编码
```

**应该改为**:
```yaml
datasource:
  url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform}
  username: ${SPRING_DATASOURCE_USERNAME:platform}
  password: ${SPRING_DATASOURCE_PASSWORD:platform123}
```

❌ **问题 2**: Redis 配置硬编码
```yaml
data:
  redis:
    host: localhost  # ❌ 硬编码
    port: 6379  # ❌ 硬编码
    password: redis123  # ❌ 硬编码
```

**应该改为**:
```yaml
data:
  redis:
    host: ${SPRING_REDIS_HOST:localhost}
    port: ${SPRING_REDIS_PORT:6379}
    password: ${SPRING_REDIS_PASSWORD:redis123}
```

❌ **问题 3**: Kafka 配置硬编码
```yaml
kafka:
  bootstrap-servers: localhost:9092  # ❌ 硬编码
```

**应该改为**:
```yaml
kafka:
  bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

❌ **问题 4**: 缺少 JWT 和加密密钥配置
- 需要添加 `JWT_SECRET` 和 `ENCRYPTION_SECRET_KEY` 环境变量支持

❌ **问题 5**: workflow-engine URL 硬编码
```yaml
workflow-engine:
  url: http://localhost:8081  # ❌ 硬编码
```

**应该改为**:
```yaml
workflow-engine:
  url: ${WORKFLOW_ENGINE_URL:http://localhost:8081}
```

---

### 3. ❌ user-portal - 需要修复

**文件**: `backend/user-portal/src/main/resources/application.yml`

❌ **问题 1**: 数据库配置硬编码
```yaml
datasource:
  url: jdbc:postgresql://localhost:5432/workflow_platform  # ❌ 硬编码
  username: platform  # ❌ 硬编码
  password: platform123  # ❌ 硬编码
```

**应该改为**:
```yaml
datasource:
  url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform}
  username: ${SPRING_DATASOURCE_USERNAME:platform}
  password: ${SPRING_DATASOURCE_PASSWORD:platform123}
```

❌ **问题 2**: 服务 URL 硬编码
```yaml
admin-center:
  url: http://localhost:8090  # ❌ 硬编码

workflow-engine:
  url: http://localhost:8081  # ❌ 硬编码
```

**应该改为**:
```yaml
admin-center:
  url: ${ADMIN_CENTER_URL:http://localhost:8090}

workflow-engine:
  url: ${WORKFLOW_ENGINE_URL:http://localhost:8081}
```

❌ **问题 3**: 缺少 Redis 配置
- 如果使用 Redis，需要添加配置

❌ **问题 4**: 缺少 JWT 和加密密钥配置
- 需要添加 `JWT_SECRET` 和 `ENCRYPTION_SECRET_KEY` 环境变量支持

---

### 4. ⚠️ developer-workstation - 部分正确

**文件**: `backend/developer-workstation/src/main/resources/application.yml`

✅ **正确使用环境变量**:
- `SPRING_REDIS_HOST: ${SPRING_REDIS_HOST:...}`
- `SPRING_REDIS_PORT: ${SPRING_REDIS_PORT:...}`
- `SPRING_REDIS_PASSWORD: ${SPRING_REDIS_PASSWORD:...}`
- `JWT_SECRET: ${JWT_SECRET:...}`

❌ **问题 1**: 数据库配置硬编码
```yaml
datasource:
  url: jdbc:postgresql://localhost:5432/workflow_platform  # ❌ 硬编码
  username: platform  # ❌ 硬编码
  password: platform123  # ❌ 硬编码
```

**应该改为**:
```yaml
datasource:
  url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform}
  username: ${SPRING_DATASOURCE_USERNAME:platform}
  password: ${SPRING_DATASOURCE_PASSWORD:platform123}
```

❌ **问题 2**: 缺少加密密钥配置
- 需要添加 `ENCRYPTION_SECRET_KEY` 环境变量支持

---

### 5. ✅ api-gateway - 正确

**文件**: `backend/api-gateway/src/main/resources/application.yml`

✅ **正确使用环境变量**:
- 所有服务 URL 都使用环境变量
- Redis 配置使用环境变量
- JWT 配置使用环境变量

---

### 6. ✅ 前端服务 - 正确

**说明**: 前端服务通过 nginx 代理访问后端，使用服务名（如 `api-gateway:8080`），这是正确的做法。

**文件**: `frontend/*/nginx.conf`
- ✅ 使用服务名进行代理（如 `http://api-gateway:8080`）
- ✅ 不需要环境变量配置

---

## 🔧 需要修复的文件

### 高优先级（必须修复）

1. `backend/admin-center/src/main/resources/application.yml`
   - 数据库配置
   - Redis 配置
   - Kafka 配置
   - JWT 和加密密钥
   - workflow-engine URL

2. `backend/user-portal/src/main/resources/application.yml`
   - 数据库配置
   - 服务 URL
   - JWT 和加密密钥

3. `backend/developer-workstation/src/main/resources/application.yml`
   - 数据库配置
   - 加密密钥

---

## 📊 docker-compose.yml 环境变量传递检查

✅ **workflow-engine**: 所有环境变量都已传递
✅ **api-gateway**: 所有环境变量都已传递
✅ **user-portal**: 所有环境变量都已传递
✅ **admin-center**: 所有环境变量都已传递
✅ **developer-workstation**: 所有环境变量都已传递

**注意**: `docker-compose.yml` 中已经正确传递了所有环境变量，但应用配置文件（`application.yml`）没有使用它们。

---

## 🎯 修复建议

1. **立即修复**: 将所有硬编码的配置改为使用环境变量
2. **统一配置**: 确保所有服务使用相同的环境变量命名规范
3. **添加缺失配置**: 为所有服务添加 JWT 和加密密钥支持
4. **测试验证**: 修复后验证 Docker 环境下的配置是否正确加载

---

## ✅ 总结

- **正确使用**: 2 个服务（workflow-engine-core, api-gateway）
- **需要修复**: 3 个服务（admin-center, user-portal, developer-workstation）
- **前端服务**: 正确（通过 nginx 代理）

**主要问题**: 多个服务的 `application.yml` 文件硬编码了配置值，没有使用环境变量，导致无法通过 `.env` 文件灵活配置。
