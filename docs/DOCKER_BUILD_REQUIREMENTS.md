# Docker 构建要求说明

**重要提示**: 在构建 Docker 镜像之前，必须先构建四个 platform 模块！

---

## 📌 重要说明：Platform 模块不需要 Dockerfile

**这四个 platform 模块是库模块（Library Modules），不是可运行的服务**：

- ✅ `platform-common` - 通用工具库
- ✅ `platform-cache` - 缓存服务库
- ✅ `platform-messaging` - 消息服务库
- ✅ `platform-security` - 安全服务库

它们：
- ❌ **不需要 Dockerfile**（因为它们不是独立服务）
- ✅ **会被打包成 JAR 文件**（`packaging>jar</packaging>`）
- ✅ **会被包含在使用它们的服务的 JAR 中**（通过 Maven 依赖）
- ✅ **会被打包到其他服务的 Docker 镜像中**

### 工作原理

当您构建 `api-gateway` 时：
1. Maven 会先构建 `platform-common`, `platform-cache`, `platform-security`（如果它们不在本地仓库中）
2. 这些模块的 JAR 文件会被打包到 `api-gateway-*.jar` 中（fat JAR）
3. Docker 镜像只包含 `api-gateway-*.jar`，其中已经包含了所有依赖

---

## ⚠️ 关键问题

您提到已经将**除了四个 platform 模块**之外的所有服务都构建并启动了。但是，**这四个模块是其他服务的必需依赖**，如果不先构建它们，其他服务**无法正常构建和运行**。

---

## 📦 四个 Platform 模块

1. **platform-common** - 通用模块（必需）
2. **platform-cache** - 缓存模块（必需）
3. **platform-messaging** - 消息模块（可选，但建议保留）
4. **platform-security** - 安全模块（核心必需）

---

## 🔗 依赖关系

### 模块依赖链

```
platform-common (基础)
    ↑
    ├── platform-cache (依赖 platform-common)
    │       ↑
    │       └── platform-security (依赖 platform-common + platform-cache)
    │
    ├── platform-messaging (依赖 platform-common)
    │
    └── platform-security (依赖 platform-common + platform-cache)
            ↑
            └── 所有其他服务依赖 platform-security
```

### 服务依赖情况

| 服务 | 依赖的 Platform 模块 |
|------|---------------------|
| **api-gateway** | platform-common, platform-security, platform-cache |
| **workflow-engine** | platform-security |
| **admin-center** | platform-security |
| **user-portal** | platform-security |
| **developer-workstation** | platform-security |

**结论**: 所有后端服务都依赖这些 platform 模块！

---

## 🚨 如果不构建这四个模块会发生什么？

### 构建阶段失败

当您尝试构建其他服务（如 `api-gateway`, `admin-center` 等）时，Maven 会报错：

```
[ERROR] Failed to execute goal on project api-gateway: 
Could not resolve dependencies for project com.platform:api-gateway:jar:1.0.0-SNAPSHOT: 
Could not find artifact com.platform:platform-common:jar:1.0.0-SNAPSHOT
Could not find artifact com.platform:platform-security:jar:1.0.0-SNAPSHOT
Could not find artifact com.platform:platform-cache:jar:1.0.0-SNAPSHOT
```

### 运行时失败

即使您跳过了构建错误（例如使用已构建的 JAR），运行时也会失败：

```
java.lang.NoClassDefFoundError: com/platform/common/dto/ApiResponse
java.lang.NoClassDefFoundError: com/platform/security/service/UserRoleService
```

---

## ✅ 正确的构建顺序

### 步骤 1: 构建 Platform 模块（必须）

按照依赖顺序构建：

```bash
# 1. 构建 platform-common（基础模块）
cd backend/platform-common
mvn clean install

# 2. 构建 platform-cache（依赖 platform-common）
cd ../platform-cache
mvn clean install

# 3. 构建 platform-messaging（依赖 platform-common，可选）
cd ../platform-messaging
mvn clean install

# 4. 构建 platform-security（依赖 platform-common + platform-cache）
cd ../platform-security
mvn clean install
```

### 步骤 2: 构建其他服务

```bash
# 构建 workflow-engine
cd ../workflow-engine-core
mvn clean package

# 构建 admin-center
cd ../admin-center
mvn clean package

# 构建 user-portal
cd ../user-portal
mvn clean package

# 构建 developer-workstation
cd ../developer-workstation
mvn clean package

# 构建 api-gateway
cd ../api-gateway
mvn clean package
```

### 步骤 3: 构建 Docker 镜像

```bash
# 从项目根目录
docker-compose build --profile backend
```

---

## 🔍 验证构建是否成功

### 检查 Maven 本地仓库

```bash
# 检查 platform 模块是否已安装到本地仓库
ls ~/.m2/repository/com/platform/

# 应该看到：
# platform-common/
# platform-cache/
# platform-messaging/
# platform-security/
```

### 检查 JAR 文件

```bash
# 检查每个服务的 target 目录
ls backend/api-gateway/target/*.jar
ls backend/admin-center/target/*.jar
ls backend/workflow-engine-core/target/*.jar
# ... 等等
```

---

## 🐳 Docker 构建流程

### 当前 Dockerfile 的工作方式

所有服务的 Dockerfile 都使用**预构建的 JAR 文件**：

```dockerfile
# 例如 api-gateway/Dockerfile
COPY target/api-gateway-*.jar app.jar
```

这意味着：
1. **必须先运行 `mvn package`** 生成 JAR 文件
2. 然后 Docker 才能复制 JAR 文件到镜像中

### Platform 模块如何被包含

Platform 模块**不需要单独的 Docker 镜像**，它们会被包含在使用它们的服务中：

```
构建流程：
1. mvn package (在 api-gateway 目录)
   ↓
2. Maven 解析依赖 → 查找 platform-common, platform-security, platform-cache
   ↓
3. 如果本地仓库没有，先构建这些模块 → 生成 JAR 文件
   ↓
4. 将这些 JAR 作为依赖打包到 api-gateway-*.jar 中（fat JAR）
   ↓
5. Docker 复制 api-gateway-*.jar → 镜像中已包含所有依赖
```

### 如果跳过 Platform 模块构建

如果您没有构建 platform 模块，但其他服务已经构建成功，可能的原因：

1. **之前已经构建过** - platform 模块的 JAR 已经在本地 Maven 仓库中（`~/.m2/repository/com/platform/`）
2. **使用了已构建的 JAR** - 直接使用了之前构建好的 JAR 文件
3. **构建失败但未发现** - 服务可能无法正常启动（运行时会出现 `NoClassDefFoundError`）

---

## ✅ 检查服务是否正常运行

### 检查服务日志

```bash
# 检查 Docker 容器日志
docker logs platform-api-gateway
docker logs platform-admin-center
docker logs platform-workflow-engine
docker logs platform-user-portal
docker logs platform-developer-workstation
```

### 查找错误信息

如果看到以下错误，说明 platform 模块未正确构建：

```
java.lang.NoClassDefFoundError: com/platform/common/...
java.lang.NoClassDefFoundError: com/platform/security/...
java.lang.NoClassDefFoundError: com/platform/cache/...
```

### 检查健康检查

```bash
# 检查服务健康状态
curl http://localhost:8080/actuator/health  # api-gateway
curl http://localhost:8090/api/v1/admin/actuator/health  # admin-center
curl http://localhost:8081/actuator/health  # workflow-engine
```

---

## 🛠️ 快速修复方案

### 如果服务已经启动但无法正常工作

1. **停止所有服务**:
   ```bash
   docker-compose down
   ```

2. **构建 platform 模块**:
   ```bash
   cd backend/platform-common && mvn clean install
   cd ../platform-cache && mvn clean install
   cd ../platform-messaging && mvn clean install
   cd ../platform-security && mvn clean install
   ```

3. **重新构建所有服务**:
   ```bash
   cd backend
   for dir in workflow-engine-core admin-center user-portal developer-workstation api-gateway; do
     cd $dir && mvn clean package && cd ..
   done
   ```

4. **重新构建 Docker 镜像**:
   ```bash
   docker-compose build --profile backend
   ```

5. **重新启动服务**:
   ```bash
   docker-compose --profile backend up -d
   ```

---

## 📝 推荐的完整构建脚本

创建一个 `build-all.sh` 脚本：

```bash
#!/bin/bash
set -e

echo "🔨 构建 Platform 模块..."
cd backend/platform-common && mvn clean install && cd ../..
cd backend/platform-cache && mvn clean install && cd ../..
cd backend/platform-messaging && mvn clean install && cd ../..
cd backend/platform-security && mvn clean install && cd ../..

echo "🔨 构建后端服务..."
cd backend/workflow-engine-core && mvn clean package && cd ../..
cd backend/admin-center && mvn clean package && cd ../..
cd backend/user-portal && mvn clean package && cd ../..
cd backend/developer-workstation && mvn clean package && cd ../..
cd backend/api-gateway && mvn clean package && cd ../..

echo "🐳 构建 Docker 镜像..."
docker-compose build --profile backend

echo "✅ 构建完成！"
```

---

## ❓ 常见问题

### Q: 我已经启动了服务，它们能正常运行吗？

**A**: 如果服务已经启动，请检查：
1. 服务日志是否有 `NoClassDefFoundError` 错误
2. 健康检查是否通过
3. API 调用是否正常

如果出现类找不到的错误，说明 platform 模块未正确构建。

### Q: 我可以跳过 platform-messaging 吗？

**A**: 可以。根据之前的分析，`platform-messaging` 当前未被使用。但其他三个模块（platform-common, platform-cache, platform-security）**必须构建**。

### Q: 为什么 Docker 构建时没有报错？

**A**: Dockerfile 只是复制已构建的 JAR 文件。如果 JAR 文件已经存在（即使依赖缺失），Docker 构建不会报错。但运行时会出现 `NoClassDefFoundError`。

### Q: 为什么 platform 模块没有 Dockerfile？

**A**: Platform 模块是**库模块**（library modules），不是可运行的服务：
- 它们被打包成 JAR 文件（`packaging>jar</packaging>`）
- 它们作为依赖被其他服务使用
- 它们会被包含在使用它们的服务的 JAR 文件中（fat JAR）
- 它们不需要单独的 Docker 镜像或容器

### Q: Platform 模块在哪里运行？

**A**: Platform 模块的代码会运行在使用它们的服务的容器中：
- `api-gateway` 容器中包含 `platform-common`, `platform-security`, `platform-cache` 的代码
- `admin-center` 容器中包含 `platform-security` 的代码
- 等等...

它们不是独立的服务，而是共享库。

### Q: 如何确认 platform 模块已正确构建？

**A**: 检查 Maven 本地仓库：
```bash
ls ~/.m2/repository/com/platform/
```

应该看到所有四个模块的目录。

---

## 🎯 总结

**关键点**:
1. ✅ **platform-common** - 必须构建（库模块，无 Dockerfile）
2. ✅ **platform-cache** - 必须构建（库模块，无 Dockerfile）
3. ⚠️ **platform-messaging** - 可选（库模块，无 Dockerfile）
4. ✅ **platform-security** - 必须构建（库模块，无 Dockerfile）

**重要理解**:
- Platform 模块是**库模块**，不是独立服务
- 它们**不需要 Dockerfile**，会被包含在使用它们的服务中
- 它们会被打包到服务的 JAR 文件中（fat JAR）
- 它们运行在使用它们的服务的容器中

**构建顺序**:
1. platform-common（`mvn install` → 安装到本地仓库）
2. platform-cache（`mvn install` → 安装到本地仓库）
3. platform-messaging（`mvn install` → 可选）
4. platform-security（`mvn install` → 安装到本地仓库）
5. 其他服务（`mvn package` → 生成包含依赖的 JAR）

**验证方法**:
- 检查 Maven 本地仓库：`~/.m2/repository/com/platform/`
- 检查服务 JAR 文件：`backend/*/target/*.jar`
- 检查服务日志：`docker logs <container-name>`
- 测试健康检查端点：`curl http://localhost:<port>/actuator/health`

**如果服务已启动但有问题**:
- 检查日志中的 `NoClassDefFoundError`
- 重新构建 platform 模块（`mvn install`）
- 重新构建服务（`mvn package`）
- 重新构建 Docker 镜像（`docker-compose build`）
- 重新启动服务（`docker-compose up -d`）
