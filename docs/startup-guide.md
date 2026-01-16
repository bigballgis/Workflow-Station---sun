# 服务启动指南

## 概述

本项目包含多个微服务，支持两种部署方式：
- **Docker 部署**：适合基础设施服务和完整环境部署
- **本地部署**：适合开发调试，支持热重载

## 服务端口一览

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| Zookeeper | 2181 | Kafka 依赖（可选） |
| Kafka | 9092 | 消息队列（可选） |
| API Gateway | 8080 | API 网关 |
| Workflow Engine Core | 8081 | 工作流引擎 |
| User Portal Backend | 8082 | 用户门户后端 |
| Developer Workstation Backend | 8083 | 开发者工作站后端 |
| Admin Center Backend | 8090 | 管理中心后端 |
| Admin Center Frontend | 3000 | 管理中心前端 |
| User Portal Frontend | 3001 | 用户门户前端 |
| Developer Workstation Frontend | 3002 | 开发者工作站前端 |

## 部署方式选择

### 推荐配置（开发环境）

| 服务类型 | 部署方式 | 原因 |
|----------|----------|------|
| PostgreSQL | Docker | 无需本地安装，数据持久化 |
| Redis | Docker | 无需本地安装，配置简单 |
| Kafka/Zookeeper | 可选 | 当前使用 Redis 模拟，不是必需 |
| 后端服务 | 本地 | 支持热重载，便于调试 |
| 前端服务 | 本地 | 支持 HMR，便于开发 |

### 完整 Docker 部署（测试/演示环境）

所有服务都使用 Docker 部署，适合快速搭建完整环境。

---

## 一、基础设施服务（Docker）

### 1. 仅启动必需服务（PostgreSQL + Redis）

```powershell
# 启动 PostgreSQL 和 Redis
docker-compose up -d postgres redis

# 验证服务状态
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### 2. 启动可选服务（Kafka + Zookeeper）

```powershell
# 如果需要消息队列功能
docker-compose up -d zookeeper kafka

# 验证 Kafka 状态
docker exec platform-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

### 3. 停止基础设施服务

```powershell
# 停止所有 Docker 服务
docker-compose down

# 停止并删除数据卷（慎用，会清除所有数据）
docker-compose down -v
```

---

## 二、后端服务

### 本地部署（推荐开发使用）

#### 前置条件
- JDK 17+
- Maven 3.8+
- 基础设施服务已启动（PostgreSQL、Redis）

#### 启动命令

```powershell
# 1. Admin Center Backend (端口 8090)
mvn spring-boot:run -pl backend/admin-center -DskipTests

# 2. User Portal Backend (端口 8082)
mvn spring-boot:run -pl backend/user-portal -DskipTests

# 3. Developer Workstation Backend (端口 8083)
mvn spring-boot:run -pl backend/developer-workstation -DskipTests

# 4. Workflow Engine Core (端口 8081)
mvn spring-boot:run -pl backend/workflow-engine-core -DskipTests

# 5. API Gateway (端口 8080)
mvn spring-boot:run -pl backend/api-gateway -DskipTests
```

#### 一键启动所有后端服务（PowerShell）

```powershell
# 在不同的 PowerShell 窗口中分别执行，或使用 Start-Process
Start-Process powershell -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run -pl backend/admin-center -DskipTests"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run -pl backend/user-portal -DskipTests"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run -pl backend/developer-workstation -DskipTests"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run -pl backend/workflow-engine-core -DskipTests"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run -pl backend/api-gateway -DskipTests"
```

### Docker 部署

```powershell
# 启动所有后端服务
docker-compose --profile backend up -d

# 或单独启动某个服务
docker-compose up -d admin-center
docker-compose up -d user-portal
docker-compose up -d developer-workstation
docker-compose up -d workflow-engine
docker-compose up -d api-gateway
```

---

## 三、前端服务

### 本地部署（推荐开发使用）

#### 前置条件
- Node.js 18+
- npm 或 yarn

#### 启动命令

```powershell
# 1. Admin Center Frontend (端口 3000)
cd frontend/admin-center
npm install  # 首次运行需要
npm run dev

# 2. User Portal Frontend (端口 3001)
cd frontend/user-portal
npm install  # 首次运行需要
npm run dev

# 3. Developer Workstation Frontend (端口 3002)
cd frontend/developer-workstation
npm install  # 首次运行需要
npm run dev
```

#### 一键启动所有前端服务（PowerShell）

```powershell
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd frontend/admin-center; npm run dev"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd frontend/user-portal; npm run dev"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd frontend/developer-workstation; npm run dev"
```

### Docker 部署

```powershell
# 构建前端镜像
docker-compose --profile frontend build

# 启动所有前端服务
docker-compose --profile frontend up -d
```

---

## 四、完整环境启动

### 方式一：混合部署（推荐开发）

```powershell
# 1. 启动基础设施
docker-compose up -d postgres redis

# 2. 等待数据库就绪（约 10 秒）
Start-Sleep -Seconds 10

# 3. 启动后端服务（在不同终端窗口）
mvn spring-boot:run -pl backend/admin-center -DskipTests
mvn spring-boot:run -pl backend/user-portal -DskipTests
mvn spring-boot:run -pl backend/developer-workstation -DskipTests
mvn spring-boot:run -pl backend/workflow-engine-core -DskipTests
mvn spring-boot:run -pl backend/api-gateway -DskipTests

# 4. 启动前端服务（在不同终端窗口）
cd frontend/admin-center && npm run dev
cd frontend/user-portal && npm run dev
cd frontend/developer-workstation && npm run dev
```

### 方式二：全 Docker 部署

```powershell
# 启动所有服务
docker-compose --profile full up -d

# 查看服务状态
docker-compose ps
```

---

## 五、服务依赖关系

```
PostgreSQL ─────┬──> Admin Center Backend
                │
Redis ──────────┼──> User Portal Backend ──────> Workflow Engine Core
                │
                ├──> Developer Workstation Backend
                │
                ├──> Workflow Engine Core
                │
                └──> API Gateway

Admin Center Frontend ──────> Admin Center Backend
User Portal Frontend ───────> User Portal Backend + API Gateway
Developer Workstation Frontend ──> Developer Workstation Backend
```

### 启动顺序建议

1. **基础设施**：PostgreSQL → Redis → (可选) Zookeeper → Kafka
2. **后端服务**：Admin Center → Workflow Engine Core → User Portal → Developer Workstation → API Gateway
3. **前端服务**：可并行启动

---

## 六、环境配置文件

### 后端配置文件位置

| 服务 | 配置文件 |
|------|----------|
| Admin Center | `backend/admin-center/src/main/resources/application-dev.yml` |
| User Portal | `backend/user-portal/src/main/resources/application-dev.yml` |
| Developer Workstation | `backend/developer-workstation/src/main/resources/application-dev.yml` |
| Workflow Engine Core | `backend/workflow-engine-core/src/main/resources/application-dev.yml` |
| API Gateway | `backend/api-gateway/src/main/resources/application-dev.yml` |

### 默认数据库配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_platform
    username: platform
    password: platform123
  data:
    redis:
      host: localhost
      port: 6379
      password: redis123
```

---

## 七、常用命令

### 检查服务状态

```powershell
# Docker 服务状态
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 检查端口占用
netstat -ano | findstr "8080 8081 8082 8083 8090 3000 3001 3002"
```

### 查看日志

```powershell
# Docker 服务日志
docker logs -f platform-postgres
docker logs -f platform-redis

# 后端服务日志（本地部署时在终端直接查看）
```

### 数据库操作

```powershell
# 连接数据库
docker exec -it platform-postgres psql -U platform -d workflow_platform

# 执行 SQL 文件
Get-Content -Path "script.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
```

### 清理环境

```powershell
# 停止所有 Docker 服务
docker-compose down

# 清理 Docker 数据卷（会删除所有数据）
docker-compose down -v

# 清理 Maven 构建缓存
mvn clean

# 清理 Node 模块
Remove-Item -Recurse -Force frontend/*/node_modules
```

---

## 八、故障排查

### 常见问题

1. **端口被占用**
   ```powershell
   # 查找占用端口的进程
   netstat -ano | findstr ":8080"
   # 终止进程
   taskkill /PID <进程ID> /F
   ```

2. **数据库连接失败**
   - 检查 PostgreSQL 容器是否运行：`docker ps | findstr postgres`
   - 检查密码配置是否正确

3. **Redis 连接失败**
   - 检查 Redis 容器是否运行：`docker ps | findstr redis`
   - 检查密码配置是否正确

4. **前端代理错误**
   - 确保对应的后端服务已启动
   - 检查 `vite.config.ts` 中的代理配置

---

## 九、访问地址

| 应用 | 地址 |
|------|------|
| Admin Center | http://localhost:3000 |
| User Portal | http://localhost:3001 |
| Developer Workstation | http://localhost:3002 |
| API Gateway | http://localhost:8080 |
| Admin Center API | http://localhost:8090/api/v1/admin |
| User Portal API | http://localhost:8082/api/portal |
| Developer Workstation API | http://localhost:8083/api/v1 |
| Workflow Engine API | http://localhost:8081/api/v1 |
