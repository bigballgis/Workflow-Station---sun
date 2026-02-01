# Platform 模块使用情况报告

**生成时间**: 2026-01-23

## 模块概览

项目包含 4 个共享平台模块：
1. **platform-common** - 通用工具和 DTO
2. **platform-cache** - Redis 缓存服务
3. **platform-messaging** - Kafka 消息服务
4. **platform-security** - JWT 认证和安全服务

---

## ✅ 1. platform-common (通用模块)

**状态**: ✅ **有用且被广泛使用**

**功能**:
- 通用 DTO (`ApiResponse`, `PageRequest`, `PageResponse`, `UserPrincipal`, `DataFilter`, `ErrorResponse`)
- 异常处理 (`BusinessException`, `ValidationException`, `PermissionDeniedException`, `GlobalExceptionHandler`)
- 工具类 (`JsonUtils`, `StringUtils`)
- 枚举 (`ErrorCode`, `Language`, `Module`)
- 审计功能 (`AuditAspect`, `AuditService`)
- 国际化 (`I18nService`)
- Saga 模式支持 (`SagaOrchestrator`, `SagaTransaction`)
- 健康检查 (`HealthIndicatorService`)
- 功能单元相关 (`FunctionUnitDeploymentService`, `ValidationResult`)

**被以下服务使用**:
- ✅ **platform-security** - 依赖 `UserPrincipal`, `DataFilter`, `ValidationException`
- ✅ **platform-cache** - 依赖 platform-common
- ✅ **platform-messaging** - 依赖 platform-common
- ✅ **api-gateway** - 依赖 platform-common（排除 web starter）

**使用示例**:
```java
// platform-security 使用
import com.platform.common.dto.UserPrincipal;
import com.platform.common.dto.DataFilter;
import com.platform.common.exception.ValidationException;
import com.platform.common.constant.PlatformConstants;
```

**结论**: ✅ **必需模块**，提供核心的通用功能和数据结构

---

## ✅ 2. platform-cache (缓存模块)

**状态**: ✅ **有用且被使用**

**功能**:
- Redis 缓存服务接口 (`CacheService`)
- Redis 实现 (`RedisCacheServiceImpl`)
- 分布式锁 (`DistributedLock`)
- 缓存失效通知

**被以下服务使用**:
- ✅ **platform-security** - 通过 `CacheService` 接口使用
  - `PermissionServiceImpl` 使用缓存服务
  - `PermissionDelegationServiceImpl` 使用缓存服务
- ✅ **api-gateway** - 依赖 platform-cache（但可能未直接使用）
- ✅ **workflow-engine-core** - 在 `WorkflowEngineApplication` 中扫描 `com.platform.cache` 包

**使用示例**:
```java
// platform-security 使用
import com.platform.cache.service.CacheService;

@Service
public class PermissionServiceImpl {
    private final CacheService cacheService;
    // 使用缓存服务进行权限缓存
}
```

**结论**: ✅ **有用模块**，提供统一的缓存抽象，被 platform-security 实际使用

---

## ⚠️ 3. platform-messaging (消息模块)

**状态**: ⚠️ **配置了但未实际使用**

**功能**:
- Kafka 事件发布接口 (`EventPublisher`)
- Kafka 实现 (`KafkaEventPublisher`)
- 事件类型 (`ProcessEvent`, `TaskEvent`, `PermissionEvent`, `DeploymentEvent`)
- 死信队列处理 (`DeadLetterHandler`)

**被以下服务使用**:
- ❌ **无服务直接依赖** - 没有任何服务的 `pom.xml` 中包含 `platform-messaging` 依赖
- ⚠️ **workflow-engine-core** - 使用 Spring 的 `ApplicationEventPublisher`，而不是 `platform-messaging` 的 `EventPublisher`
- ⚠️ **只有测试文件** - 仅在 `platform-messaging` 自己的测试文件中使用

**检查结果**:
- `workflow-engine-core` 使用 `ApplicationEventPublisher`（Spring 内置），不是 `platform-messaging` 的 `EventPublisher`
- 没有服务在 `pom.xml` 中声明 `platform-messaging` 依赖
- `platform-messaging` 模块存在但未被集成到任何服务中

**结论**: ⚠️ **未使用模块**，可以：
1. **删除** - 如果不需要 Kafka 事件发布功能
2. **保留** - 如果计划在未来使用 Kafka 进行事件驱动架构

**建议**: 如果当前不需要 Kafka 事件发布，可以考虑删除以简化项目结构

---

## ✅ 4. platform-security (安全模块)

**状态**: ✅ **核心模块，被所有服务使用**

**功能**:
- JWT 令牌生成和验证 (`JwtTokenService`, `JwtTokenServiceImpl`)
- 用户认证 (`AuthenticationService`, `AuthenticationServiceImpl`)
- 用户角色服务 (`UserRoleService`, `UserRoleServiceImpl`)
- 权限服务 (`PermissionService`, `PermissionServiceImpl`)
- 权限委托 (`PermissionDelegationService`)
- 登录审计 (`LoginAuditService`)
- 加密服务 (`EncryptionService`, `AesEncryptionService`)
- JWT 认证过滤器 (`JwtAuthenticationFilter`)
- 目标解析器 (`TargetResolver`, `UserTargetResolver`, `VirtualGroupTargetResolver`)
- 实体 (`LoginAudit`, `RoleAssignment`)
- 控制器 (`AuthController`)

**被以下服务使用**:
- ✅ **workflow-engine** - 依赖 platform-security
- ✅ **admin-center** - 依赖 platform-security，使用 `UserRoleService`, `TargetResolverFactory`
- ✅ **user-portal** - 依赖 platform-security，使用 `UserRoleService`, `UserEffectiveRole`
- ✅ **developer-workstation** - 依赖 platform-security，使用 `UserRoleService`, `TargetResolverFactory`
- ✅ **api-gateway** - 依赖 platform-security（排除 web starter）

**使用示例**:
```java
// 所有服务都使用
import com.platform.security.service.UserRoleService;
import com.platform.security.resolver.TargetResolverFactory;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.entity.LoginAudit;
```

**扫描配置**:
- `workflow-engine-core`: `@SpringBootApplication(scanBasePackages = {"com.workflow", "com.platform.cache", "com.platform.security"})`
- `admin-center`, `user-portal`, `developer-workstation`: 通过 `@ComponentScan` 扫描特定类

**结论**: ✅ **核心必需模块**，提供所有服务的认证和授权功能

---

## 模块依赖关系

```
platform-common (基础模块)
    ↑
    ├── platform-cache (依赖 platform-common)
    │       ↑
    │       └── platform-security (依赖 platform-common + platform-cache)
    │
    ├── platform-messaging (依赖 platform-common)
    │
    └── platform-security (依赖 platform-common + platform-cache)
```

---

## 使用统计

| 模块 | 被依赖的服务数 | 实际使用 | 状态 |
|------|--------------|---------|------|
| **platform-common** | 4+ | ✅ 是 | ✅ 必需 |
| **platform-cache** | 2 | ✅ 是 | ✅ 有用 |
| **platform-messaging** | 0 | ❌ 否 | ⚠️ 未使用 |
| **platform-security** | 5 | ✅ 是 | ✅ 核心 |

---

## 总结和建议

### ✅ 必需保留的模块

1. **platform-common** ✅
   - **原因**: 提供核心的 DTO、异常、工具类，被所有其他模块依赖
   - **使用情况**: 被 platform-security, platform-cache, platform-messaging, api-gateway 使用
   - **建议**: **必须保留**

2. **platform-security** ✅
   - **原因**: 提供 JWT 认证、权限管理，被所有后端服务使用
   - **使用情况**: 被 workflow-engine, admin-center, user-portal, developer-workstation, api-gateway 使用
   - **建议**: **必须保留**

### ✅ 有用的模块

3. **platform-cache** ✅
   - **原因**: 提供统一的缓存抽象，被 platform-security 使用
   - **使用情况**: 被 platform-security 和 api-gateway 依赖
   - **建议**: **建议保留**，提供缓存抽象层

### ⚠️ 未使用的模块

4. **platform-messaging** ⚠️
   - **原因**: 定义了 Kafka 事件发布接口，但没有任何服务实际使用
   - **使用情况**: 无服务依赖，只有自己的测试文件使用
   - **建议**: 
     - **选项 1**: 如果不需要 Kafka 事件驱动，可以**删除**以简化项目
     - **选项 2**: 如果计划使用事件驱动架构，可以**保留**但需要集成到服务中

---

## 详细使用情况

### platform-common 提供的核心功能

1. **DTO 类**:
   - `ApiResponse<T>` - 统一 API 响应格式
   - `UserPrincipal` - 用户主体信息
   - `PageRequest` / `PageResponse` - 分页请求/响应
   - `DataFilter` - 数据过滤
   - `ErrorResponse` - 错误响应

2. **异常类**:
   - `BusinessException` - 业务异常
   - `ValidationException` - 验证异常
   - `PermissionDeniedException` - 权限拒绝异常
   - `ResourceNotFoundException` - 资源未找到异常
   - `GlobalExceptionHandler` - 全局异常处理

3. **工具类**:
   - `JsonUtils` - JSON 工具
   - `StringUtils` - 字符串工具

### platform-cache 提供的功能

1. **缓存服务**:
   - `CacheService` - 缓存服务接口
   - `RedisCacheServiceImpl` - Redis 实现
   - 支持字符串和对象缓存
   - 支持 TTL 和过期时间管理

2. **分布式锁**:
   - `DistributedLock` - 分布式锁接口
   - Redis 实现

3. **缓存失效**:
   - 支持发布缓存失效消息

### platform-messaging 提供的功能（未使用）

1. **事件发布**:
   - `EventPublisher` - 事件发布接口
   - `KafkaEventPublisher` - Kafka 实现
   - 支持 `ProcessEvent`, `TaskEvent`, `PermissionEvent`, `DeploymentEvent`

2. **配置**:
   - `KafkaConfig` - Kafka 配置
   - `KafkaTopics` - 主题定义

### platform-security 提供的功能

1. **认证**:
   - JWT 令牌生成和验证
   - 登录/登出功能
   - 令牌刷新
   - 令牌黑名单（使用 Redis）

2. **授权**:
   - 用户角色服务
   - 权限服务
   - 权限委托
   - 目标解析器（用户、虚拟组等）

3. **安全**:
   - 加密服务（AES）
   - 登录审计
   - JWT 认证过滤器

---

## 最终建议

### 必须保留 ✅
- **platform-common** - 基础模块，所有其他模块依赖
- **platform-security** - 核心安全模块，所有服务使用

### 建议保留 ✅
- **platform-cache** - 提供缓存抽象，被 platform-security 使用

### 可选删除 ⚠️
- **platform-messaging** - 当前未使用，如果不需要 Kafka 事件驱动可以删除

**如果删除 platform-messaging**:
- 不会影响任何现有功能
- 可以简化项目结构
- 如果未来需要，可以重新添加
