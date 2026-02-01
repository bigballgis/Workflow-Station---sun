# 后端 Application.yml 文件标准化

## 概述

已完成所有后端模块的 application.yml 文件标准化，移除了 profile 配置，统一了格式和结构。

## 标准化原则

1. **移除 Profile 配置**: 不再使用 `spring.profiles.active` 和 `---` 分隔的 profile 配置
2. **统一格式**: 所有配置文件使用相同的结构和缩进
3. **环境变量**: 所有配置值通过环境变量传递，提供合理的默认值
4. **日志配置**: 统一日志格式和文件输出配置
5. **数据库配置**: 统一 Hikari 连接池配置
6. **Redis 配置**: 为每个服务分配不同的数据库编号

## 已标准化的模块

### 1. admin-center (端口: 8092)
- **Context Path**: `/api/v1/admin`
- **Redis Database**: 1
- **特殊配置**: 
  - Security 密码策略
  - Admin 审计配置
  - 完整的 Hikari 连接池配置

### 2. api-gateway (端口: 8090)
- **Context Path**: `/`
- **Redis Database**: 默认
- **特殊配置**: 
  - Spring Cloud Gateway 路由配置
  - Rate Limiting 配置
  - CORS 配置

### 3. developer-workstation (端口: 8094)
- **Context Path**: `/`
- **Redis Database**: 2
- **特殊配置**: 
  - Admin Center 集成配置
  - 文件上传限制 (10MB)

### 4. user-portal (端口: 8093)
- **Context Path**: `/api/portal`
- **Redis Database**: 0
- **特殊配置**: 
  - Workflow Engine 集成配置
  - Admin Center 集成配置

### 5. workflow-engine-core (端口: 8091)
- **Context Path**: `/`
- **Redis Database**: 3
- **特殊配置**: 
  - Flowable 配置
  - Kafka 配置
  - Admin Center 集成配置

### 6. platform-messaging
- **特殊配置**: 
  - Kafka 生产者/消费者配置
  - 消息序列化配置

## 统一配置结构

每个 application.yml 文件都遵循以下结构：

```yaml
server:
  port: ${SERVER_PORT:默认端口}
  servlet:
    context-path: /context-path  # 如果需要

spring:
  application:
    name: 服务名
  
  datasource:
    # 数据库配置
  
  jpa:
    # JPA 配置
  
  flyway:
    # Flyway 配置
  
  redis:
    # Redis 配置
  
  kafka:
    # Kafka 配置 (如果需要)
  
  servlet:
    multipart:
      # 文件上传配置

  jackson:
    # JSON 序列化配置

# 业务配置
admin-center:
  url: ${ADMIN_CENTER_URL:http://localhost:8092}

workflow-engine:
  url: ${WORKFLOW_ENGINE_URL:http://localhost:8091}

# JWT 配置
jwt:
  secret: ${JWT_SECRET_KEY:workflow-engine-jwt-secret-key-2026}
  expiration: 86400000
  refresh-expiration: 604800000

# 平台配置
platform:
  encryption:
    secret-key: ${ENCRYPTION_KEY:workflow-aes-256-encryption-key!}

# 日志配置
logging:
  level:
    root: INFO
    com.模块: DEBUG
  file:
    name: logs/服务名.log
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [服务名] [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [服务名] [%thread] %-5level %logger{36} - %msg%n"
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 30

# Actuator 配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized

# OpenAPI 配置
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
```

## 环境变量映射

| 环境变量 | 默认值 | 用途 |
|---------|--------|------|
| `SERVER_PORT` | 各服务默认端口 | 服务端口 |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/workflow_platform?currentSchema=projectx` | 数据库连接 |
| `SPRING_DATASOURCE_USERNAME` | `platform` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | `platform123` | 数据库密码 |
| `SPRING_REDIS_HOST` | `localhost` | Redis 主机 |
| `SPRING_REDIS_PORT` | `6379` | Redis 端口 |
| `SPRING_REDIS_PASSWORD` | `redis123` | Redis 密码 |
| `JWT_SECRET_KEY` | `workflow-engine-jwt-secret-key-2026` | JWT 密钥 |
| `ENCRYPTION_KEY` | `workflow-aes-256-encryption-key!` | 加密密钥 |
| `ADMIN_CENTER_URL` | `http://localhost:8092` | Admin Center 服务地址 |
| `WORKFLOW_ENGINE_URL` | `http://localhost:8091` | Workflow Engine 服务地址 |

## Redis 数据库分配

| 服务 | Redis Database | 用途 |
|------|----------------|------|
| user-portal | 0 | 用户会话和缓存 |
| admin-center | 1 | 管理员会话和缓存 |
| developer-workstation | 2 | 开发工作站缓存 |
| workflow-engine-core | 3 | 工作流引擎缓存 |
| api-gateway | 默认 | 网关缓存和限流 |

## 日志文件输出

所有服务的日志文件统一输出到 `logs/` 目录：

- `logs/admin-center.log`
- `logs/api-gateway.log`
- `logs/developer-workstation.log`
- `logs/user-portal.log`
- `logs/workflow-engine.log`

## 验证清单

- [x] 移除所有 profile 配置 (`spring.profiles.active` 和 `---` 分隔符)
- [x] 统一日志格式和文件输出
- [x] 统一 Hikari 连接池配置
- [x] 统一 JWT 和加密配置
- [x] 统一 Actuator 和 OpenAPI 配置
- [x] 为每个服务分配不同的 Redis 数据库
- [x] 统一环境变量命名规范
- [x] 添加必要的业务配置 (admin-center, workflow-engine)

## 注意事项

1. **构建时**: 现在直接使用 `application.yml`，不需要指定 profile
2. **环境变量**: 通过环境变量控制不同环境的配置
3. **日志级别**: 生产环境可通过环境变量调整日志级别
4. **数据库**: 所有服务使用相同的数据库但不同的 schema (projectx)
5. **Redis**: 每个服务使用不同的数据库编号避免冲突

## 后续维护

1. 新增服务时，参考此标准格式创建 application.yml
2. 修改配置时，保持格式一致性
3. 新增环境变量时，更新此文档的映射表
4. 定期检查配置文件的一致性