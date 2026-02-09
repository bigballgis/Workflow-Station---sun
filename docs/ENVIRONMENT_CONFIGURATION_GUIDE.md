# 环境配置指南

**日期**: 2026-02-02  
**版本**: 1.0

## 概述

本文档详细说明了 Workflow Platform 在不同环境（DEV、SIT、UAT、PROD）下的配置差异和部署要求。

---

## 环境列表

| 环境 | 全称 | 用途 | 部署方式 |
|------|------|------|----------|
| **DEV** | Development | 本地开发和调试 | Docker Compose |
| **SIT** | System Integration Testing | 系统集成测试 | Kubernetes |
| **UAT** | User Acceptance Testing | 用户验收测试 | Kubernetes |
| **PROD** | Production | 生产环境 | Kubernetes |

---

## 配置文件位置

### DEV 环境
```
deploy/environments/dev/.env
deploy/environments/dev/docker-compose.dev.yml
```

### SIT 环境
```
deploy/k8s/configmap-sit.yaml
deploy/k8s/secret-sit.yaml
deploy/k8s/deployment-*.yaml (namespace: workflow-platform-sit)
```

### UAT 环境
```
deploy/k8s/configmap-uat.yaml
deploy/k8s/secret-uat.yaml
deploy/k8s/deployment-*-uat.yaml (namespace: workflow-platform-uat)
```

### PROD 环境
```
deploy/k8s/configmap-prod.yaml
deploy/k8s/secret-prod.yaml
deploy/k8s/deployment-*-prod.yaml (namespace: workflow-platform-prod)
```

---

## 详细配置对比

### 1. 数据库配置

| 配置项 | DEV | SIT | UAT | PROD |
|--------|-----|-----|-----|------|
| **数据库名** | workflow_platform_dev | workflow_platform | workflow_platform_uat | workflow_platform_prod |
| **用户名** | platform_dev | platform | platform_uat | platform_prod |
| **密码强度** | 简单 (开发用) | 中等 (32字符) | 强 (32字符) | 非常强 (64字符) |
| **连接池大小** | 20 | 20 | 30 | 50 |
| **最小空闲连接** | 5 | 5 | 10 | 10 |
| **连接超时** | 20秒 | 20秒 | 30秒 | 30秒 |
| **Schema 更新** | ✅ 自动 | ❌ 手动 | ❌ 手动 | ❌ 手动 |

### 2. 日志配置

| 配置项 | DEV | SIT | UAT | PROD |
|--------|-----|-----|-----|------|
| **Root 日志级别** | DEBUG | INFO | INFO | WARN |
| **Platform 日志级别** | DEBUG | INFO | INFO | INFO |
| **SQL 日志级别** | DEBUG | WARN | WARN | ERROR |
| **显示 SQL** | ✅ 是 | ❌ 否 | ❌ 否 | ❌ 否 |
| **格式化 SQL** | ✅ 是 | ❌ 否 | ❌ 否 | ❌ 否 |
| **日志文件** | ./logs | K8s 日志 | K8s 日志 | K8s 日志 |

### 3. 安全配置

| 配置项 | DEV | SIT | UAT | PROD |
|--------|-----|-----|-----|------|
| **密码最小长度** | 6 | 8 | 10 | 12 |
| **密码复杂度** | 低 | 中 | 高 | 非常高 |
| **登录失败次数** | 10 | 5 | 3 | 3 |
| **锁定时长** | 30分钟 | 30分钟 | 30分钟 | 60分钟 |
| **会话超时** | 60分钟 | 30分钟 | 30分钟 | 15分钟 |
| **JWT 过期时间** | 24小时 | 24小时 | 12小时 | 8小时 |
| **Refresh Token** | 7天 | 7天 | 3天 | 1天 |
| **JWT 密钥长度** | 256-bit | 256-bit | 256-bit | 256-bit |

### 4. 缓存配置

| 配置项 | DEV | SIT | UAT | PROD |
|--------|-----|-----|-----|------|
| **Redis 数据库** | 0 | 0 | 0 | 0 |
| **用户缓存 TTL** | 15分钟 | 30分钟 | 45分钟 | 60分钟 |
| **权限缓存 TTL** | 30分钟 | 60分钟 | 90分钟 | 120分钟 |
| **字典缓存 TTL** | 60分钟 | 120分钟 | 180分钟 | 240分钟 |
| **连接池大小** | 8 | 8 | 16 | 32 |

### 5. 服务配置

| 配置项 | DEV | SIT | UAT | PROD |
|--------|-----|-----|-----|------|
| **副本数** | 1 | 2 | 2 | 3+ |
| **CPU 请求** | - | 500m | 500m | 1000m |
| **CPU 限制** | - | 1000m | 1000m | 2000m |
| **内存请求** | - | 512Mi | 512Mi | 1Gi |
| **内存限制** | - | 1Gi | 1Gi | 2Gi |
| **健康检查延迟** | - | 90秒 | 90秒 | 120秒 |
| **就绪检查延迟** | - | 60秒 | 60秒 | 90秒 |

### 6. 功能开关

| 功能 | DEV | SIT | UAT | PROD |
|------|-----|-----|-----|------|
| **Swagger UI** | ✅ 启用 | ✅ 启用 | ❌ 禁用 | ❌ 禁用 |
| **Actuator 详情** | always | always | when_authorized | never |
| **SQL 显示** | ✅ 是 | ❌ 否 | ❌ 否 | ❌ 否 |
| **开发工具** | ✅ 启用 | ❌ 禁用 | ❌ 禁用 | ❌ 禁用 |
| **热重载** | ✅ 启用 | ❌ 禁用 | ❌ 禁用 | ❌ 禁用 |

### 7. 监控配置

| 配置项 | DEV | SIT | UAT | PROD |
|--------|-----|-----|-----|------|
| **Actuator** | ✅ 启用 | ✅ 启用 | ✅ 启用 | ✅ 启用 |
| **Health 端点** | ✅ 公开 | ✅ 公开 | ✅ 公开 | ✅ 公开 |
| **Metrics 端点** | ✅ 公开 | ✅ 公开 | ✅ 公开 | ✅ 公开 |
| **Info 端点** | ✅ 公开 | ✅ 公开 | ✅ 公开 | ✅ 公开 |
| **详细信息** | ✅ 显示 | ✅ 显示 | 🔐 授权后 | ❌ 隐藏 |
| **审计日志** | ✅ 启用 | ✅ 启用 | ✅ 启用 | ✅ 启用 |

---

## 环境变量清单

### 必需的环境变量

所有环境都必须配置以下环境变量：

```bash
# Spring Profile
SPRING_PROFILES_ACTIVE=dev|sit|uat|prod

# 数据库配置
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/database
SPRING_DATASOURCE_USERNAME=username
SPRING_DATASOURCE_PASSWORD=password

# Redis 配置
SPRING_REDIS_HOST=redis-host
SPRING_REDIS_PORT=6379
SPRING_REDIS_PASSWORD=redis-password

# JWT 配置
JWT_SECRET=your-256-bit-secret-key
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

# 加密配置
ENCRYPTION_SECRET_KEY=your-32-byte-encryption-key
```

### 可选的环境变量

```bash
# 日志配置
LOG_LEVEL_ROOT=INFO
LOG_LEVEL_PLATFORM=DEBUG
LOG_LEVEL_SQL=WARN

# 安全配置
SECURITY_PASSWORD_MIN_LENGTH=8
SECURITY_LOGIN_MAX_FAILED_ATTEMPTS=5
SECURITY_SESSION_TIMEOUT_MINUTES=30

# 缓存配置
CACHE_USER_TTL_MINUTES=30
CACHE_PERMISSION_TTL_MINUTES=60
CACHE_DICTIONARY_TTL_MINUTES=120

# 服务 URL
ADMIN_CENTER_URL=http://admin-center:8080
WORKFLOW_ENGINE_URL=http://workflow-engine:8080
```

---

## 部署检查清单

### DEV 环境部署前检查

- [ ] Docker 和 Docker Compose 已安装
- [ ] PostgreSQL 容器正常运行
- [ ] Redis 容器正常运行
- [ ] `.env` 文件已配置
- [ ] 数据库初始化脚本已执行

### SIT 环境部署前检查

- [ ] K8s 集群可访问
- [ ] Namespace `workflow-platform-sit` 已创建
- [ ] ConfigMap 已配置并应用
- [ ] Secret 已配置并应用（密码已修改）
- [ ] 数据库已创建并初始化
- [ ] Redis 已部署并可访问
- [ ] 镜像已推送到镜像仓库

### UAT 环境部署前检查

- [ ] K8s 集群可访问
- [ ] Namespace `workflow-platform-uat` 已创建
- [ ] ConfigMap 已配置并应用
- [ ] Secret 已配置并应用（使用强密码）
- [ ] 数据库已创建并初始化
- [ ] Redis 已部署并可访问
- [ ] 镜像已推送到镜像仓库
- [ ] 网络策略已配置
- [ ] RBAC 权限已配置

### PROD 环境部署前检查

- [ ] K8s 集群可访问
- [ ] Namespace `workflow-platform-prod` 已创建
- [ ] ConfigMap 已配置并应用
- [ ] Secret 已配置并应用（使用非常强的密码）
- [ ] 数据库已创建并初始化（使用备份恢复）
- [ ] Redis 已部署并可访问（使用持久化）
- [ ] 镜像已推送到镜像仓库（使用特定版本标签）
- [ ] 网络策略已配置
- [ ] RBAC 权限已配置
- [ ] 资源配额已设置
- [ ] 监控和告警已配置
- [ ] 备份策略已实施
- [ ] 灾难恢复计划已制定
- [ ] 安全审计已完成
- [ ] 变更管理流程已批准

---

## 密钥生成指南

### JWT Secret (256-bit)

```bash
# 生成 JWT Secret
openssl rand -base64 32

# 示例输出
# 8xK9mP2nQ5rS7tU1vW3xY4zA6bC8dE0fG2hI4jK6lM8=
```

### Encryption Key (32 字符)

```bash
# 生成加密密钥
openssl rand -base64 32 | cut -c1-32

# 示例输出
# 9yL0nP3oQ6rS8tU2vW4xY5zA7bC9
```

### 数据库密码

```bash
# DEV 环境（简单密码）
echo "dev_password_123"

# SIT 环境（中等强度，32 字符）
openssl rand -base64 24

# UAT 环境（强密码，32 字符）
openssl rand -base64 24

# PROD 环境（非常强的密码，64 字符）
openssl rand -base64 48
```

---

## 配置验证

### 验证 Spring Profile

```bash
# 检查当前激活的 Profile
curl http://localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("profiles"))'
```

### 验证数据库连接

```bash
# 检查数据库健康状态
curl http://localhost:8080/actuator/health | jq '.components.db'
```

### 验证 Redis 连接

```bash
# 检查 Redis 健康状态
curl http://localhost:8080/actuator/health | jq '.components.redis'
```

### 验证配置加载

```bash
# 查看所有配置（需要授权）
curl -u admin:password http://localhost:8080/actuator/configprops
```

---

## 故障排查

### 问题：服务无法启动

**可能原因**:
1. 数据库连接失败
2. Redis 连接失败
3. 配置文件错误
4. 端口被占用

**解决方法**:
```bash
# 检查日志
docker logs <container-name>
kubectl logs <pod-name> -n <namespace>

# 检查环境变量
docker exec <container-name> env | grep SPRING
kubectl exec <pod-name> -n <namespace> -- env | grep SPRING

# 检查网络连接
docker exec <container-name> ping postgres
kubectl exec <pod-name> -n <namespace> -- ping postgres
```

### 问题：配置未生效

**可能原因**:
1. 环境变量未设置
2. Profile 未激活
3. 配置优先级问题

**解决方法**:
```bash
# 检查激活的 Profile
curl http://localhost:8080/actuator/env | jq '.activeProfiles'

# 检查配置来源
curl http://localhost:8080/actuator/env | jq '.propertySources'

# 重启服务
docker restart <container-name>
kubectl rollout restart deployment/<deployment-name> -n <namespace>
```

---

## 最佳实践

### 1. 配置管理

✅ **推荐做法**:
- 使用环境变量注入配置
- 敏感信息使用 Secret 管理
- 配置文件使用占位符
- 不同环境使用不同的密钥

❌ **避免做法**:
- 在代码中硬编码配置
- 在配置文件中硬编码密码
- 所有环境使用相同的密钥
- 将 Secret 提交到版本控制

### 2. 安全配置

✅ **推荐做法**:
- 生产环境使用强密码（64+ 字符）
- 定期轮换密钥（每 90 天）
- 限制 Actuator 端点访问
- 启用审计日志

❌ **避免做法**:
- 使用弱密码或默认密码
- 长期不更换密钥
- 公开暴露管理端点
- 禁用安全功能

### 3. 资源配置

✅ **推荐做法**:
- 根据负载设置合理的资源限制
- 配置健康检查和就绪检查
- 使用多副本提高可用性
- 配置自动扩缩容

❌ **避免做法**:
- 不设置资源限制
- 跳过健康检查
- 单副本部署到生产
- 手动扩缩容

---

## 参考文档

- [Spring Boot 配置文档](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html)
- [Kubernetes ConfigMap 文档](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [Kubernetes Secret 文档](https://kubernetes.io/docs/concepts/configuration/secret/)
- [Spring Profiles 详解](docs/SPRING_PROFILES_EXPLANATION.md)
- [K8s 部署指南](deploy/k8s/README-DEPLOYMENT.md)

---

## 更新历史

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|----------|------|
| 2026-02-02 | 1.0 | 初始版本，添加 UAT 和 PROD 环境配置 | Kiro |
