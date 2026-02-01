# Redis 使用情况报告

**生成时间**: 2026-01-23  
**数据库**: Redis 7.2-alpine  
**容器名**: platform-redis

## 使用 Redis 的服务列表

### ✅ 1. workflow-engine (工作流引擎核心服务)

**端口**: 8081  
**配置文件**: 
- `backend/workflow-engine-core/src/main/resources/application.yml`
- `docker-compose.yml` (lines 110-150)

**Redis 用途**:
- ✅ **权限缓存** (`SecurityManagerComponent`): 缓存用户权限信息，TTL 24小时
- ✅ **安全事件存储** (`SecurityManagerComponent`): 存储登录成功/失败事件，TTL 30天
- ✅ **令牌黑名单** (`SecurityManagerComponent`): 管理 JWT 令牌黑名单，TTL 7天
- ✅ **角色缓存** (`SecurityManagerComponent`): 缓存用户角色信息，TTL 30天
- ✅ **性能数据缓存** (`PerformanceManagerComponent`): 缓存流程定义、流程实例、任务等性能数据
- ✅ **通知管理** (`NotificationManagerComponent`): 管理通知消息
- ✅ **水平扩展** (`HorizontalScalingComponent`): 分布式锁和状态同步
- ✅ **数据访问安全** (`DataAccessSecurityComponent`): 数据访问权限缓存

**Redis 配置**:
```yaml
spring:
  data:
    redis:
      host: ${SPRING_REDIS_HOST:localhost}
      port: ${SPRING_REDIS_PORT:6379}
      password: ${SPRING_REDIS_PASSWORD:redis123}
      timeout: 5000ms
```

**依赖关系**: 
- `depends_on`: redis (condition: service_healthy)

---

### ✅ 2. admin-center (管理后台服务)

**端口**: 8090  
**配置文件**: 
- `backend/admin-center/src/main/resources/application.yml`
- `backend/admin-center/src/main/resources/application-docker.yml`
- `docker-compose.yml` (lines 152-192)

**Redis 用途**:
- ✅ **缓存配置** (application.yml 中配置了缓存 TTL):
  - 用户缓存: 30分钟
  - 权限缓存: 60分钟
  - 字典缓存: 120分钟
- ⚠️ **代码中未直接使用 RedisTemplate**，可能通过 Spring Cache 抽象层使用

**Redis 配置**:
```yaml
spring:
  data:
    redis:
      host: ${SPRING_REDIS_HOST:localhost}
      port: ${SPRING_REDIS_PORT:6379}
      password: ${SPRING_REDIS_PASSWORD:redis123}
      database: 1  # 使用数据库 1
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 3000ms
```

**依赖关系**: 
- `depends_on`: redis (condition: service_healthy)

---

### ✅ 3. user-portal (用户门户服务)

**端口**: 8082  
**配置文件**: 
- `backend/user-portal/src/main/resources/application-docker.yml`
- `docker-compose.yml` (lines 195-236)

**Redis 用途**:
- ⚠️ **配置了 Redis 但代码中未直接使用**
- 可能通过 `platform-security` 模块间接使用（JWT 令牌黑名单）

**Redis 配置**:
```yaml
spring:
  data:
    redis:
      host: ${SPRING_REDIS_HOST:redis}
      port: ${SPRING_REDIS_PORT:6379}
      password: ${SPRING_REDIS_PASSWORD:${REDIS_PASSWORD:-redis123}}
```

**依赖关系**: 
- `depends_on`: redis (condition: service_healthy)

---

### ✅ 4. developer-workstation (开发工作站服务)

**端口**: 8083  
**配置文件**: 
- `backend/developer-workstation/src/main/resources/application.yml`
- `docker-compose.yml` (lines 238-276)

**Redis 用途**:
- ⚠️ **配置了 Redis 但代码中未直接使用**
- 可能预留用于未来功能（如缓存、会话管理等）

**Redis 配置**:
```yaml
spring:
  data:
    redis:
      host: ${SPRING_REDIS_HOST:localhost}
      port: ${SPRING_REDIS_PORT:6379}
      password: ${SPRING_REDIS_PASSWORD:redis123}
      timeout: 5000ms
```

**依赖关系**: 
- `depends_on`: redis (condition: service_healthy)

---

### ✅ 5. api-gateway (API 网关服务)

**端口**: 8080  
**配置文件**: 
- `backend/api-gateway/src/main/resources/application.yml`
- `docker-compose.yml` (lines 278-319)

**Redis 用途**:
- ✅ **限流控制** (`RateLimitFilter`): 使用 `ReactiveRedisTemplate` 实现分布式限流
  - 默认限制: 100 请求/60秒
  - 登录接口: 10 请求/60秒
  - 刷新令牌: 20 请求/60秒
  - 注册接口: 5 请求/60秒

**Redis 配置**:
```yaml
spring:
  data:
    redis:
      host: ${SPRING_REDIS_HOST:localhost}
      port: ${SPRING_REDIS_PORT:6379}
      password: ${SPRING_REDIS_PASSWORD:redis123}
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

**依赖关系**: 
- `depends_on`: redis (condition: service_healthy)

---

## 共享模块使用 Redis

### ✅ platform-security (安全模块)

**用途**:
- ✅ **JWT 令牌黑名单** (`JwtTokenServiceImpl`): 
  - 存储已注销的令牌
  - 键格式: `auth:blacklist:{tokenId}`
  - TTL: 7天

**使用方式**: 被所有需要认证的服务共享使用

---

### ✅ platform-cache (缓存模块)

**用途**:
- ✅ **通用缓存服务** (`RedisCacheServiceImpl`):
  - 提供统一的缓存接口
  - 支持分布式锁 (`DistributedLock`)
  - 支持缓存失效通知
  - 支持字符串和对象缓存

**使用方式**: 可被其他服务通过依赖注入使用

---

## Redis 使用统计

| 服务 | 直接使用 | 间接使用 | 主要用途 |
|------|---------|---------|---------|
| **workflow-engine** | ✅ 是 | - | 权限缓存、安全事件、性能缓存、通知管理 |
| **admin-center** | ⚠️ 否 | ✅ 可能 | 用户/权限/字典缓存（通过 Spring Cache） |
| **user-portal** | ⚠️ 否 | ✅ 是 | JWT 令牌黑名单（通过 platform-security） |
| **developer-workstation** | ⚠️ 否 | - | 预留（未实际使用） |
| **api-gateway** | ✅ 是 | - | 分布式限流 |
| **platform-security** | ✅ 是 | - | JWT 令牌黑名单 |
| **platform-cache** | ✅ 是 | - | 通用缓存服务 |

## Redis 数据库分配

根据配置，不同服务可能使用不同的 Redis 数据库：

- **admin-center**: 使用 `database: 1`
- **其他服务**: 使用默认数据库 `database: 0`

## Redis 键命名规范

### workflow-engine 使用的键前缀：
- `workflow:process_def:{id}` - 流程定义缓存
- `workflow:process_inst:{id}` - 流程实例缓存
- `workflow:task:{id}` - 任务缓存
- `workflow:variable:{id}` - 变量缓存
- `workflow:statistics:{key}` - 统计信息缓存
- `security:permission:{userId}` - 用户权限缓存
- `security:role:{userId}` - 用户角色缓存
- `security:event:{eventType}:{id}` - 安全事件
- `security:token:{username}` - 用户令牌
- `auth:blacklist:{tokenId}` - 令牌黑名单

### api-gateway 使用的键：
- `rate_limit:{path}:{userId}` - 限流计数器

## 总结

**使用 Redis 的服务总数**: 5 个后端服务

1. ✅ **workflow-engine** - 核心使用，大量功能依赖 Redis
2. ✅ **api-gateway** - 用于分布式限流
3. ✅ **admin-center** - 配置了 Redis，可能通过 Spring Cache 使用
4. ⚠️ **user-portal** - 配置了 Redis，通过 platform-security 间接使用
5. ⚠️ **developer-workstation** - 配置了 Redis，但未实际使用

**建议**:
- `user-portal` 和 `developer-workstation` 虽然配置了 Redis，但代码中未直接使用，可以考虑：
  1. 如果确实不需要，可以移除 Redis 依赖以简化部署
  2. 如果需要，可以添加实际的缓存功能来利用 Redis
