# Spring Profiles 工作机制详解

**日期**: 2026-02-02

## 1. Spring Profiles 是什么？

Spring Profiles 是 Spring Framework 提供的环境配置机制，允许你为不同的运行环境（开发、测试、生产等）定义不同的配置。

### 工作原理

1. **配置文件加载顺序**:
   - `application.yml` (基础配置，总是加载)
   - `application-{profile}.yml` (特定环境配置，覆盖基础配置)

2. **激活方式**:
   - 环境变量: `SPRING_PROFILES_ACTIVE=dev`
   - JVM 参数: `-Dspring.profiles.active=dev`
   - application.yml 中: `spring.profiles.active: dev`

3. **配置合并**:
   ```
   application.yml (基础)
   + application-dev.yml (开发环境覆盖)
   = 最终生效的配置
   ```

---

## 2. 你的项目配置分析

### 2.1 Maven Profiles (pom.xml)

```xml
<profiles>
    <profile>
        <id>dev</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <properties>
            <spring.profiles.active>dev</spring.profiles.active>
        </properties>
    </profile>
    <profile>
        <id>test</id>
        <properties>
            <spring.profiles.active>test</spring.profiles.active>
        </properties>
    </profile>
    <profile>
        <id>prod</id>
        <properties>
            <spring.profiles.active>prod</spring.profiles.active>
        </properties>
    </profile>
</profiles>
```

**重要**: Maven Profiles 只在**编译打包时**生效，用于：
- 控制编译参数
- 包含/排除特定资源文件
- 设置默认的 Spring Profile

**不影响运行时**: 运行时的 Spring Profile 由环境变量 `SPRING_PROFILES_ACTIVE` 决定！

### 2.2 Spring Profiles 配置文件

你的项目中存在的 Spring Profile 配置文件：

#### Admin Center
- `application.yml` (基础配置)
- `application-docker.yml` (Docker 环境)

#### Workflow Engine
- `application.yml` (基础配置)
- `application-dev.yml` (开发环境)
- `application-docker.yml` (Docker 环境)

#### User Portal
- `application.yml` (基础配置)
- `application-test.yml` (测试环境)
- `application-docker.yml` (Docker 环境)

#### Developer Workstation
- `application.yml` (基础配置)
- `application-docker.yml` (Docker 环境)

---

## 3. 不同环境的配置差异

### 3.1 DEV 环境 (开发环境)

**配置来源**: `deploy/environments/dev/.env`

```bash
SPRING_PROFILES_ACTIVE=dev
POSTGRES_DB=workflow_platform_dev
POSTGRES_USER=platform_dev
POSTGRES_PASSWORD=dev_password_123
LOG_LEVEL=DEBUG
SQL_SHOW=true
SWAGGER_ENABLED=true
```

**特点**:
- 使用本地数据库 (localhost:5432)
- 详细日志 (DEBUG 级别)
- 显示 SQL 语句
- 启用 Swagger 文档
- 宽松的安全策略 (密码最小长度 6，失败尝试 10 次)

### 3.2 SIT 环境 (系统集成测试)

**配置来源**: `deploy/k8s/configmap-sit.yaml` + `deploy/k8s/secret-sit.yaml`

```yaml
SPRING_PROFILES_ACTIVE: "sit"
LOG_LEVEL_ROOT: "INFO"
LOG_LEVEL_PLATFORM: "INFO"
SECURITY_PASSWORD_MIN_LENGTH: "8"
SECURITY_LOGIN_MAX_FAILED_ATTEMPTS: "5"
SECURITY_SESSION_TIMEOUT_MINUTES: "30"
```

**特点**:
- 使用远程数据库 (K8s 集群内)
- 中等日志级别 (INFO)
- 更严格的安全策略
- 使用 K8s Service 名称进行服务间调用

### 3.3 UAT 环境 (用户验收测试)

**当前状态**: ❌ **未配置**

你的项目中**没有** UAT 环境的配置文件：
- 没有 `application-uat.yml`
- 没有 `configmap-uat.yaml`
- Maven pom.xml 中也没有 `uat` profile

### 3.4 PROD 环境 (生产环境)

**当前状态**: ❌ **未配置**

Maven pom.xml 中定义了 `prod` profile，但：
- 没有 `application-prod.yml` 配置文件
- 没有 K8s 配置文件

---

## 4. 关键问题解答

### Q1: Maven Profile 中没有 UAT 和 SIT，会有影响吗？

**答案**: ✅ **不会有影响**

**原因**:
1. **Maven Profile 只在编译时生效**，用于控制编译行为
2. **运行时的环境由 `SPRING_PROFILES_ACTIVE` 环境变量决定**
3. 你可以在 K8s 中设置 `SPRING_PROFILES_ACTIVE=sit` 或 `SPRING_PROFILES_ACTIVE=uat`，即使 Maven pom.xml 中没有对应的 profile

**示例**:
```bash
# 编译时使用 Maven dev profile
mvn clean package -Pdev

# 运行时使用 Spring sit profile
docker run -e SPRING_PROFILES_ACTIVE=sit your-image
```

### Q2: 如果设置 SPRING_PROFILES_ACTIVE=sit，但没有 application-sit.yml 会怎样？

**答案**: ⚠️ **会使用 application.yml 的默认配置**

**行为**:
1. Spring Boot 会尝试加载 `application-sit.yml`
2. 如果文件不存在，不会报错
3. 只使用 `application.yml` 中的配置
4. 通过环境变量覆盖配置 (如 K8s ConfigMap 中的配置)

**这就是你的项目的设计方式**:
- 基础配置在 `application.yml`
- 环境特定配置通过**环境变量**注入 (K8s ConfigMap/Secret)
- 不需要为每个环境创建单独的 `application-{env}.yml` 文件

### Q3: DEV、SIT、UAT 环境的主要差异是什么？

| 配置项 | DEV | SIT | UAT | PROD |
|--------|-----|-----|-----|------|
| **数据库** | 本地/开发库 | 测试库 | 预生产库 | 生产库 |
| **日志级别** | DEBUG | INFO | INFO | WARN/ERROR |
| **SQL 显示** | ✅ 是 | ❌ 否 | ❌ 否 | ❌ 否 |
| **Swagger** | ✅ 启用 | ✅ 启用 | ❌ 禁用 | ❌ 禁用 |
| **密码策略** | 宽松 (6位) | 中等 (8位) | 严格 (10位) | 严格 (12位) |
| **失败尝试** | 10次 | 5次 | 3次 | 3次 |
| **会话超时** | 60分钟 | 30分钟 | 30分钟 | 15分钟 |
| **缓存 TTL** | 短 (15/30/60) | 中 (30/60/120) | 长 | 长 |
| **监控** | 基础 | 完整 | 完整 | 完整 |

---

## 5. 你的项目配置策略

### 当前策略: **环境变量驱动配置**

```
application.yml (基础配置，使用占位符)
    ↓
环境变量 (Docker .env / K8s ConfigMap)
    ↓
最终配置
```

**优点**:
- ✅ 不需要为每个环境创建配置文件
- ✅ 配置集中管理 (K8s ConfigMap)
- ✅ 敏感信息分离 (K8s Secret)
- ✅ 易于部署到不同环境

**示例** (application.yml):
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform}
    username: ${SPRING_DATASOURCE_USERNAME:platform}
    password: ${SPRING_DATASOURCE_PASSWORD:platform123}
```

**环境变量覆盖** (K8s ConfigMap):
```yaml
SPRING_DATASOURCE_URL: "jdbc:postgresql://sit-postgres:5432/workflow_platform"
SPRING_DATASOURCE_USERNAME: "platform_sit"
```

---

## 6. 建议的配置完善

### 6.1 添加 UAT 环境配置

如果需要 UAT 环境，创建以下文件：

#### 1. K8s ConfigMap
```bash
deploy/k8s/configmap-uat.yaml
```

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: workflow-platform-config
  namespace: workflow-platform-uat
data:
  SPRING_PROFILES_ACTIVE: "uat"
  LOG_LEVEL_ROOT: "INFO"
  SECURITY_PASSWORD_MIN_LENGTH: "10"
  SECURITY_LOGIN_MAX_FAILED_ATTEMPTS: "3"
  # ... 其他配置
```

#### 2. K8s Secret
```bash
deploy/k8s/secret-uat.yaml
```

#### 3. 可选: application-uat.yml
```bash
backend/*/src/main/resources/application-uat.yml
```

**注意**: 如果使用环境变量驱动配置，这个文件是**可选的**。

### 6.2 添加 PROD 环境配置

同样的方式创建:
- `deploy/k8s/configmap-prod.yaml`
- `deploy/k8s/secret-prod.yaml`
- (可选) `application-prod.yml`

---

## 7. 最佳实践建议

### 7.1 配置优先级

Spring Boot 配置加载优先级 (从高到低):
1. 命令行参数
2. 环境变量 (SPRING_DATASOURCE_URL)
3. application-{profile}.yml
4. application.yml

### 7.2 敏感信息处理

✅ **正确做法**:
- 数据库密码 → K8s Secret
- JWT 密钥 → K8s Secret
- 加密密钥 → K8s Secret

❌ **错误做法**:
- 不要在 application.yml 中硬编码密码
- 不要在代码中硬编码密钥
- 不要提交 .env 文件到 Git

### 7.3 环境隔离

每个环境应该有:
- ✅ 独立的数据库
- ✅ 独立的 Redis
- ✅ 独立的 K8s Namespace
- ✅ 不同的安全策略

---

## 8. 总结

### 关键点

1. **Maven Profile ≠ Spring Profile**
   - Maven Profile: 编译时
   - Spring Profile: 运行时

2. **你的项目不需要为 SIT/UAT 添加 Maven Profile**
   - 运行时通过 `SPRING_PROFILES_ACTIVE` 环境变量控制
   - 配置通过 K8s ConfigMap/Secret 注入

3. **当前缺失的配置**
   - ❌ UAT 环境的 K8s 配置文件
   - ❌ PROD 环境的 K8s 配置文件
   - ✅ 但这不影响系统运行，只是需要在部署到这些环境时创建

4. **推荐的配置方式**
   - 基础配置: `application.yml` (使用占位符)
   - 环境差异: K8s ConfigMap + Secret
   - 不需要为每个环境创建 `application-{env}.yml`

### 下一步行动

如果需要部署到 UAT/PROD 环境，需要:
1. 创建 `deploy/k8s/configmap-uat.yaml` 和 `configmap-prod.yaml`
2. 创建 `deploy/k8s/secret-uat.yaml` 和 `secret-prod.yaml`
3. 复制并修改 deployment YAML 文件，更新 namespace
4. 配置不同的安全策略和资源限制
