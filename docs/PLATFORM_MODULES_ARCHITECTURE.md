# Platform 模块架构说明

## 📦 模块类型

项目中有两种类型的模块：

### 1. 库模块（Library Modules）- 不需要 Dockerfile

这些模块提供共享功能，被其他服务作为依赖使用：

- ✅ `platform-common` - 通用工具和 DTO
- ✅ `platform-cache` - Redis 缓存服务
- ✅ `platform-messaging` - Kafka 消息服务
- ✅ `platform-security` - JWT 认证和安全服务

**特点**:
- `packaging>jar</packaging>` - 打包成 JAR 文件
- 没有 `main` 方法 - 不是可运行的应用
- 没有 Dockerfile - 不需要单独的容器
- 作为依赖被其他服务使用

### 2. 服务模块（Service Modules）- 需要 Dockerfile

这些模块是可运行的 Spring Boot 应用：

- ✅ `api-gateway` - API 网关服务
- ✅ `workflow-engine-core` - 工作流引擎服务
- ✅ `admin-center` - 管理后台服务
- ✅ `user-portal` - 用户门户服务
- ✅ `developer-workstation` - 开发者工作站服务

**特点**:
- `packaging>jar</packaging>` - 打包成可执行 JAR
- 有 `main` 方法 - 可独立运行
- 有 Dockerfile - 需要单独的容器
- 依赖 platform 模块

---

## 🔄 构建和打包流程

### Maven 构建流程

```
1. 构建 platform-common
   mvn install
   → 生成 platform-common-1.0.0-SNAPSHOT.jar
   → 安装到 ~/.m2/repository/com/platform/platform-common/

2. 构建 platform-cache
   mvn install
   → 生成 platform-cache-1.0.0-SNAPSHOT.jar
   → 安装到 ~/.m2/repository/com/platform/platform-cache/
   → 依赖 platform-common（从本地仓库获取）

3. 构建 platform-security
   mvn install
   → 生成 platform-security-1.0.0-SNAPSHOT.jar
   → 安装到 ~/.m2/repository/com/platform/platform-security/
   → 依赖 platform-common 和 platform-cache（从本地仓库获取）

4. 构建 api-gateway
   mvn package
   → 解析依赖：platform-common, platform-security, platform-cache
   → 从本地仓库获取这些 JAR
   → 使用 Spring Boot Maven Plugin 打包成 fat JAR
   → 生成 api-gateway-1.0.0-SNAPSHOT.jar（包含所有依赖）
```

### Fat JAR 结构

当您构建 `api-gateway` 时，生成的 JAR 文件结构：

```
api-gateway-1.0.0-SNAPSHOT.jar
├── BOOT-INF/
│   ├── classes/                    # api-gateway 的类
│   │   └── com/platform/gateway/
│   └── lib/                         # 所有依赖的 JAR
│       ├── platform-common-1.0.0-SNAPSHOT.jar
│       ├── platform-security-1.0.0-SNAPSHOT.jar
│       ├── platform-cache-1.0.0-SNAPSHOT.jar
│       ├── spring-boot-*.jar
│       └── ... (其他依赖)
└── META-INF/
    └── MANIFEST.MF                  # 包含 Main-Class
```

### Docker 构建流程

```
1. Dockerfile 复制 fat JAR
   COPY target/api-gateway-*.jar app.jar
   
2. 运行 JAR
   java -jar app.jar
   
3. Spring Boot 从 fat JAR 中加载所有类
   - api-gateway 的类
   - platform-common 的类
   - platform-security 的类
   - platform-cache 的类
   - 所有其他依赖
```

---

## 🏗️ 运行时架构

### 容器中的代码分布

```
┌─────────────────────────────────────┐
│  api-gateway 容器                    │
│  ┌───────────────────────────────┐  │
│  │ api-gateway-*.jar (fat JAR)   │  │
│  │ ├── api-gateway 代码          │  │
│  │ ├── platform-common 代码     │  │
│  │ ├── platform-security 代码    │  │
│  │ └── platform-cache 代码       │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  admin-center 容器                   │
│  ┌───────────────────────────────┐  │
│  │ admin-center-*.jar (fat JAR) │  │
│  │ ├── admin-center 代码        │  │
│  │ └── platform-security 代码   │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

**关键点**:
- Platform 模块的代码运行在使用它们的服务的容器中
- 每个服务容器都包含它需要的 platform 模块代码
- Platform 模块不是独立的服务，不需要单独的容器

---

## 📋 为什么 Platform 模块不需要 Dockerfile？

### 1. 它们不是可运行的应用

```java
// platform-common 没有 main 方法
// 它只提供工具类和 DTO

// api-gateway 有 main 方法
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

### 2. 它们作为依赖被使用

```xml
<!-- api-gateway/pom.xml -->
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>platform-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 3. 它们会被打包到服务的 JAR 中

Spring Boot Maven Plugin 会创建一个 fat JAR，包含所有依赖。

### 4. 它们运行在服务的容器中

当 `api-gateway` 容器启动时，它会加载 fat JAR 中的所有类，包括 platform 模块的类。

---

## ✅ 正确的构建和部署流程

### 本地开发

```bash
# 1. 构建所有模块（包括 platform 模块）
mvn clean install -DskipTests

# 2. 运行服务（会使用本地仓库中的 platform 模块）
cd backend/api-gateway
mvn spring-boot:run
```

### Docker 部署

```bash
# 1. 构建所有模块（包括 platform 模块）
mvn clean install -DskipTests

# 2. 打包服务（会包含 platform 模块）
mvn clean package -DskipTests -pl backend/api-gateway,backend/workflow-engine-core,backend/admin-center,backend/user-portal,backend/developer-workstation -am

# 3. 构建 Docker 镜像（只构建服务，platform 模块已包含在 JAR 中）
docker-compose build --profile backend

# 4. 启动服务
docker-compose --profile backend up -d
```

---

## 🔍 验证 Platform 模块是否正确包含

### 检查 JAR 文件内容

```bash
# 查看 api-gateway JAR 中包含的依赖
jar -tf backend/api-gateway/target/api-gateway-*.jar | grep platform

# 应该看到：
# BOOT-INF/lib/platform-common-1.0.0-SNAPSHOT.jar
# BOOT-INF/lib/platform-security-1.0.0-SNAPSHOT.jar
# BOOT-INF/lib/platform-cache-1.0.0-SNAPSHOT.jar
```

### 检查容器中的类

```bash
# 进入容器
docker exec -it platform-api-gateway sh

# 查看 JAR 内容
jar -tf app.jar | grep platform

# 应该看到 platform 模块的类
```

---

## 📝 总结

| 模块类型 | 示例 | 需要 Dockerfile? | 如何部署 |
|--------|------|-----------------|---------|
| **库模块** | platform-common, platform-security | ❌ 否 | 作为依赖包含在服务 JAR 中 |
| **服务模块** | api-gateway, admin-center | ✅ 是 | 构建 Docker 镜像并运行容器 |

**关键理解**:
- Platform 模块 = 共享库（像 npm 包或 Python 库）
- 服务模块 = 可运行的应用（像 Node.js 应用或 Python 脚本）
- Platform 模块的代码最终运行在服务模块的容器中
