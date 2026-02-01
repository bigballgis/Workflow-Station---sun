# 本地构建和运行指南（不使用 Docker）

本文档介绍如何在本地构建和运行项目，不使用 Docker 构建镜像。

## 📋 前置要求

### 必需环境

1. **Java 17+**
   ```powershell
   java -version
   # 应该显示 java version "17.x.x" 或更高
   ```

2. **Maven 3.9+**
   ```powershell
   mvn --version
   # 应该显示 Apache Maven 3.9.x 或更高
   ```

3. **Node.js 20+**
   ```powershell
   node --version
   # 应该显示 v20.x.x 或更高
   ```

4. **npm**（通常随 Node.js 一起安装）
   ```powershell
   npm --version
   ```

### 基础设施服务（可选，可以使用 Docker）

- PostgreSQL 16+（或使用 Docker 运行）
- Redis 7+（或使用 Docker 运行）
- Kafka（可选，或使用 Docker 运行）

## 🏗️ 构建步骤

### 步骤 1：构建后端服务（Maven）

#### 方式一：构建所有后端服务（推荐）

```powershell
# 在项目根目录执行
mvn clean package -DskipTests
```

**说明：**
- `clean`：清理之前的构建
- `package`：打包成 JAR 文件
- `-DskipTests`：跳过测试（加快构建速度）

**构建时间：** 约 5-15 分钟（取决于网络和机器性能）

#### 方式二：单独构建每个服务

```powershell
# 构建 API Gateway
cd backend/api-gateway
mvn clean package -DskipTests
cd ../..

# 构建 Workflow Engine
cd backend/workflow-engine-core
mvn clean package -DskipTests
cd ../..

# 构建 Admin Center
cd backend/admin-center
mvn clean package -DskipTests
cd ../..

# 构建 Developer Workstation
cd backend/developer-workstation
mvn clean package -DskipTests
cd ../..

# 构建 User Portal
cd backend/user-portal
mvn clean package -DskipTests
cd ../..
```

#### 验证 JAR 文件

```powershell
# 检查所有 JAR 文件是否已生成
dir backend\api-gateway\target\*.jar
dir backend\workflow-engine-core\target\*.jar
dir backend\admin-center\target\*.jar
dir backend\developer-workstation\target\*.jar
dir backend\user-portal\target\*.jar
```

应该看到类似 `admin-center-1.0.0-SNAPSHOT.jar` 的文件。

### 步骤 2：构建前端服务（npm）

#### 方式一：构建所有前端服务

```powershell
# 构建 Admin Center 前端
cd frontend/admin-center
npm install
npm run build
cd ../..

# 构建 User Portal 前端
cd frontend/user-portal
npm install
npm run build
cd ../..

# 构建 Developer Workstation 前端
cd frontend/developer-workstation
npm install
npm run build
cd ../..
```

#### 方式二：使用脚本（如果已创建）

```powershell
# 使用现有的启动脚本（会自动安装依赖）
.\start-frontend.ps1
```

**说明：**
- `npm install`：安装依赖（首次运行需要）
- `npm run build`：构建生产版本
- `npm run dev`：开发模式运行（热重载）

## 🚀 运行服务

### 方式一：使用启动脚本（推荐）

#### Windows PowerShell

```powershell
# 1. 启动基础设施服务（PostgreSQL, Redis 等）
docker-compose up -d postgres redis

# 2. 启动后端服务
.\start-backend.ps1

# 3. 启动前端服务（新开一个终端）
.\start-frontend.ps1
```

#### Linux/macOS

```bash
# 1. 启动基础设施服务
docker-compose up -d postgres redis

# 2. 启动后端服务
./start-backend.sh

# 3. 启动前端服务（新开一个终端）
./start-frontend.sh
```

### 方式二：手动启动（开发调试）

#### 启动后端服务

**需要为每个服务打开一个终端窗口：**

```powershell
# 终端 1 - API Gateway
cd backend/api-gateway
mvn spring-boot:run

# 终端 2 - Workflow Engine
cd backend/workflow-engine-core
mvn spring-boot:run

# 终端 3 - Admin Center
cd backend/admin-center
mvn spring-boot:run

# 终端 4 - User Portal
cd backend/user-portal
mvn spring-boot:run

# 终端 5 - Developer Workstation
cd backend/developer-workstation
mvn spring-boot:run
```

#### 启动前端服务

**需要为每个前端打开一个终端窗口：**

```powershell
# 终端 6 - Frontend Admin
cd frontend/admin-center
npm install  # 首次运行需要
npm run dev

# 终端 7 - Frontend Portal
cd frontend/user-portal
npm install  # 首次运行需要
npm run dev

# 终端 8 - Frontend Developer
cd frontend/developer-workstation
npm install  # 首次运行需要
npm run dev
```

### 方式三：使用 JAR 文件运行（生产模式）

```powershell
# 运行 API Gateway
java -jar backend/api-gateway/target/api-gateway-*.jar

# 运行 Workflow Engine
java -jar backend/workflow-engine-core/target/workflow-engine-core-*.jar

# 运行 Admin Center
java -jar backend/admin-center/target/admin-center-*.jar

# 运行 User Portal
java -jar backend/user-portal/target/user-portal-*.jar

# 运行 Developer Workstation
java -jar backend/developer-workstation/target/developer-workstation-*.jar
```

## 📝 配置文件

### 后端服务配置

后端服务使用 `application.yml` 配置文件，通常位于：
- `backend/[service-name]/src/main/resources/application.yml`

**关键配置：**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_platform
    username: platform
    password: platform123
  redis:
    host: localhost
    port: 6379
    password: redis123
```

### 前端服务配置

前端服务使用环境变量或配置文件，通常位于：
- `frontend/[service-name]/.env`
- `frontend/[service-name]/vite.config.ts`

## 🔍 验证服务运行

### 检查后端服务

```powershell
# 检查端口是否被占用
netstat -ano | findstr :8080
netstat -ano | findstr :8081
netstat -ano | findstr :8090

# 或者使用 curl 测试
curl http://localhost:8090/api/v1/admin/actuator/health
```

### 检查前端服务

```powershell
# 在浏览器中访问
# http://localhost:3000  # Admin Center
# http://localhost:3001  # User Portal
# http://localhost:3002  # Developer Workstation
```

## 🛑 停止服务

### 使用脚本停止

```powershell
# Windows
.\stop-backend.ps1
.\stop-frontend.ps1

# Linux/macOS
./stop-backend.sh
./stop-frontend.sh
```

### 手动停止

```powershell
# 查找 Java 进程
Get-Process | Where-Object {$_.ProcessName -like "*java*"}

# 停止特定进程
Stop-Process -Id <PID>

# 或者使用端口查找并停止
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

## 📊 服务端口列表

| 服务 | 端口 | 说明 |
|------|------|------|
| API Gateway | 8080 | 后端 API 网关 |
| Workflow Engine | 8081 | 工作流引擎 |
| User Portal Backend | 8082 | 用户门户后端 |
| Developer Workstation Backend | 8083 | 开发者工作站后端 |
| Admin Center Backend | 8090 | 管理后台后端 |
| Frontend Admin | 3000 | 管理后台前端 |
| Frontend Portal | 3001 | 用户门户前端 |
| Frontend Developer | 3002 | 开发者工作站前端 |
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |

## 🔧 开发模式 vs 生产模式

### 开发模式（推荐）

**优点：**
- 热重载，修改代码自动生效
- 详细的错误信息
- 快速调试

**启动方式：**
```powershell
# 后端
mvn spring-boot:run

# 前端
npm run dev
```

### 生产模式

**优点：**
- 性能更好
- 资源占用更少

**启动方式：**
```powershell
# 后端
java -jar target/*.jar

# 前端
npm run build
# 然后使用 nginx 或其他服务器提供静态文件
```

## 🐛 常见问题

### 问题 1：Maven 构建失败

**错误：** 依赖下载失败

**解决方案：**
```powershell
# 配置 Maven 镜像（编辑 ~/.m2/settings.xml）
# 添加阿里云镜像
```

### 问题 2：npm install 失败

**错误：** 网络超时或依赖冲突

**解决方案：**
```powershell
# 使用国内镜像
npm config set registry https://registry.npmmirror.com

# 清理缓存
npm cache clean --force

# 删除 node_modules 重新安装
rm -rf node_modules package-lock.json
npm install
```

### 问题 3：端口被占用

**解决方案：**
```powershell
# 查找占用端口的进程
netstat -ano | findstr :8080

# 停止进程
taskkill /PID <PID> /F
```

### 问题 4：数据库连接失败

**解决方案：**
```powershell
# 确保 PostgreSQL 正在运行
docker-compose ps postgres

# 检查连接配置
# 查看 application.yml 中的数据库配置
```

## 📚 快速参考

### 完整构建和启动流程

```powershell
# 1. 构建后端
mvn clean package -DskipTests

# 2. 启动基础设施（如果还没有）
docker-compose up -d postgres redis

# 3. 启动后端服务
.\start-backend.ps1

# 4. 启动前端服务（新终端）
.\start-frontend.ps1

# 5. 访问服务
# http://localhost:3000
```

### 只构建不运行

```powershell
# 只构建后端 JAR
mvn clean package -DskipTests

# 只构建前端（生产版本）
cd frontend/admin-center && npm run build
```

## 💡 提示

1. **开发时使用 `mvn spring-boot:run`**：支持热重载，修改代码后自动重启
2. **生产部署使用 JAR 文件**：性能更好，资源占用更少
3. **前端开发使用 `npm run dev`**：支持热重载，实时预览
4. **使用启动脚本**：更方便，自动管理进程和日志
