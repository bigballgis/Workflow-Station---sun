# 项目可执行脚本清单

生成时间: 2026-01-18

本文档列出了项目中所有可执行的脚本和命令。

---

## 📋 目录

1. [Shell 脚本 (Bash)](#shell-脚本-bash)
2. [PowerShell 脚本](#powershell-脚本)
3. [NPM 脚本 (前端)](#npm-脚本-前端)
4. [Maven 命令 (后端)](#maven-命令-后端)
5. [Docker Compose 命令](#docker-compose-命令)
6. [数据库脚本 (SQL)](#数据库脚本-sql)

---

## Shell 脚本 (Bash)

### 根目录脚本

#### 1. `start-services.sh`
**位置**: 项目根目录  
**功能**: 启动项目前后端服务的统一入口脚本  
**用法**: `./start-services.sh`  
**说明**: 
- 检查基础设施服务（PostgreSQL, Redis, Kafka, Zookeeper）
- 提供两种启动方式：
  - Docker Compose 模式（推荐）
  - 本地开发模式（需要 Java 17+ 和 Node.js 20+）

#### 2. `start-backend.sh`
**位置**: 项目根目录  
**功能**: 启动所有后端服务（本地开发模式）  
**用法**: `./start-backend.sh`  
**启动的服务**:
- API Gateway (端口 8080)
- Workflow Engine (端口 8081)
- Admin Center (端口 8090)
- Developer Workstation (端口 8083)
- User Portal (端口 8082)

**日志位置**: `logs/*.log`  
**PID 文件**: `logs/*.pid`

#### 3. `start-frontend.sh`
**位置**: 项目根目录  
**功能**: 启动所有前端服务（本地开发模式）  
**用法**: `./start-frontend.sh`  
**启动的服务**:
- Frontend Admin (端口 3000)
- Frontend Portal (端口 3001)
- Frontend Developer (端口 3002)

**日志位置**: `logs/frontend-*.log`  
**PID 文件**: `logs/frontend-*.pid`

#### 4. `stop-backend.sh`
**位置**: 项目根目录  
**功能**: 停止所有后端服务  
**用法**: `./stop-backend.sh`  
**说明**: 通过 PID 文件停止所有后端服务进程

#### 5. `stop-frontend.sh`
**位置**: 项目根目录  
**功能**: 停止所有前端服务  
**用法**: `./stop-frontend.sh`  
**说明**: 通过 PID 文件停止所有前端服务进程

---

## PowerShell 脚本

### Windows 环境脚本

#### 1. `start-all.ps1`
**位置**: 项目根目录  
**功能**: Windows 环境下启动所有服务  
**用法**: `.\start-all.ps1`  
**说明**: Windows PowerShell 版本的启动脚本

#### 2. `stop-all.ps1`
**位置**: 项目根目录  
**功能**: Windows 环境下停止所有服务  
**用法**: `.\stop-all.ps1`  
**说明**: Windows PowerShell 版本的停止脚本

---

## NPM 脚本 (前端)

### Frontend Admin Center (`frontend/admin-center/package.json`)

```bash
cd frontend/admin-center

# 开发模式
npm run dev          # 启动开发服务器 (端口 3000)

# 构建
npm run build        # 构建生产版本

# 预览
npm run preview      # 预览构建结果

# 测试
npm test             # 运行测试（watch 模式）
npm run test:run     # 运行测试（单次）

# 代码检查
npm run lint         # 运行 ESLint 并自动修复
```

### Frontend User Portal (`frontend/user-portal/package.json`)

```bash
cd frontend/user-portal

# 开发模式
npm run dev          # 启动开发服务器 (端口 3001)

# 构建
npm run build        # 构建生产版本

# 预览
npm run preview      # 预览构建结果

# 代码检查
npm run lint         # 运行 ESLint 并自动修复
```

### Frontend Developer Workstation (`frontend/developer-workstation/package.json`)

```bash
cd frontend/developer-workstation

# 开发模式
npm run dev          # 启动开发服务器 (端口 3002)

# 构建
npm run build        # 构建生产版本

# 预览
npm run preview      # 预览构建结果

# 测试
npm test             # 运行测试（watch 模式）
npm run test:watch   # 运行测试（watch 模式，别名）

# 代码检查
npm run lint         # 运行 ESLint 并自动修复
```

---

## Maven 命令 (后端)

### 通用 Maven 命令

所有后端模块都支持以下 Maven 命令：

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests

# 运行 Spring Boot 应用
mvn spring-boot:run

# 安装到本地仓库
mvn clean install
```

### 后端服务模块

#### 1. API Gateway
```bash
cd backend/api-gateway
mvn spring-boot:run
# 端口: 8080
```

#### 2. Workflow Engine Core
```bash
cd backend/workflow-engine-core
mvn spring-boot:run
# 端口: 8081
```

#### 3. Admin Center
```bash
cd backend/admin-center
mvn spring-boot:run
# 端口: 8090
```

#### 4. User Portal
```bash
cd backend/user-portal
mvn spring-boot:run
# 端口: 8082
```

#### 5. Developer Workstation
```bash
cd backend/developer-workstation
mvn spring-boot:run
# 端口: 8083
```

---

## Docker Compose 命令

### 基础设施服务

```bash
# 启动基础设施服务
docker-compose up -d postgres redis kafka zookeeper

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f [service-name]

# 停止服务
docker-compose down
```

### 后端服务（Docker Compose）

```bash
# 启动所有后端服务
docker-compose --profile backend up -d

# 启动单个服务
docker-compose up -d api-gateway
docker-compose up -d workflow-engine
docker-compose up -d admin-center
docker-compose up -d user-portal
docker-compose up -d developer-workstation
```

### 前端服务（Docker Compose）

```bash
# 启动所有前端服务
docker-compose --profile frontend up -d

# 启动单个服务
docker-compose up -d frontend-admin
docker-compose up -d frontend-portal
docker-compose up -d frontend-developer
```

### 完整启动

```bash
# 启动所有服务
docker-compose --profile backend --profile frontend up -d

# 查看所有服务状态
docker-compose ps

# 查看所有服务日志
docker-compose logs -f
```

---

## 数据库脚本 (SQL)

### 部署脚本 (`deploy/scripts/`)

#### 1. `fix-user-table-constraints.sql`
**功能**: 修复用户表约束  
**说明**: 移除 `sys_users` 表中 `email` 和 `full_name` 的 NOT NULL 约束  
**用法**: 
```bash
PGPASSWORD=platform123 psql -h localhost -p 5432 -U platform -d workflow_platform -f deploy/scripts/fix-user-table-constraints.sql
```

#### 2. `fix-extended-task-info-id.sql`
**功能**: 修复扩展任务信息表的 ID 字段类型  
**说明**: 将 `wf_extended_task_info.id` 从 `VARCHAR(64)` 改为 `BIGSERIAL`  
**用法**: 
```bash
PGPASSWORD=platform123 psql -h localhost -p 5432 -U platform -d workflow_platform -f deploy/scripts/fix-extended-task-info-id.sql
```

#### 3. `fix-user-passwords.sql`
**功能**: 修复用户密码哈希  
**说明**: 将所有测试用户的密码统一为 `admin123`  
**用法**: 
```bash
PGPASSWORD=platform123 psql -h localhost -p 5432 -U platform -d workflow_platform -f deploy/scripts/fix-user-passwords.sql
```

### Flyway 迁移脚本

#### Platform Security (`backend/platform-security/src/main/resources/db/migration/`)
- `V1__init_schema.sql` - 初始化平台安全核心表结构
- `V2__init_data.sql` - 初始化测试数据

#### Workflow Engine Core (`backend/workflow-engine-core/src/main/resources/db/migration/`)
- `V1__init_schema.sql` - 初始化工作流引擎核心表结构

#### User Portal (`backend/user-portal/src/main/resources/db/migration/`)
- `V1__init_schema.sql` - 初始化用户门户表结构

#### Admin Center (`backend/admin-center/src/main/resources/db/migration/`)
- `V1__init_schema.sql` - 初始化管理中心表结构

#### Developer Workstation (`backend/developer-workstation/src/main/resources/db/migration/`)
- `V1__init_schema.sql` - 初始化开发工作站表结构
- `V2__init_data.sql` - 初始化测试数据
- `V3__init_process.sql` - 初始化流程数据

---

## 快速参考

### 启动所有服务（推荐方式）

```bash
# 方式 1: 使用统一脚本
./start-services.sh

# 方式 2: 使用 Docker Compose
docker-compose --profile backend --profile frontend up -d

# 方式 3: 分别启动
./start-backend.sh
./start-frontend.sh
```

### 停止所有服务

```bash
# 方式 1: 使用 Docker Compose
docker-compose down

# 方式 2: 分别停止
./stop-backend.sh
./stop-frontend.sh
```

### 查看服务状态

```bash
# Docker Compose 服务
docker-compose ps

# 本地进程（通过 PID 文件）
ps aux | grep -E "(java|node)" | grep -E "(8080|8081|8082|8083|8090|3000|3001|3002)"
```

### 查看日志

```bash
# Docker Compose 日志
docker-compose logs -f [service-name]

# 本地服务日志
tail -f logs/*.log
tail -f logs/frontend-*.log
```

---

## 服务端口映射

| 服务 | 端口 | 访问地址 |
|------|------|----------|
| API Gateway | 8080 | http://localhost:8080 |
| Workflow Engine | 8081 | http://localhost:8081 |
| User Portal (Backend) | 8082 | http://localhost:8082 |
| Developer Workstation (Backend) | 8083 | http://localhost:8083 |
| Admin Center (Backend) | 8090 | http://localhost:8090 |
| Frontend Admin | 3000 | http://localhost:3000 |
| Frontend Portal | 3001 | http://localhost:3001 |
| Frontend Developer | 3002 | http://localhost:3002 |
| PostgreSQL | 5432 | localhost:5432 |
| Redis | 6379 | localhost:6379 |
| Kafka | 9092 | localhost:9092 |
| Zookeeper | 2181 | localhost:2181 |

---

## 注意事项

1. **环境要求**:
   - Java 17+
   - Node.js 20+
   - Docker & Docker Compose
   - PostgreSQL 14+
   - Maven 3.8+

2. **首次运行**:
   - 需要先启动基础设施服务（PostgreSQL, Redis, Kafka, Zookeeper）
   - 前端需要先运行 `npm install` 安装依赖
   - 后端会自动运行 Flyway 迁移脚本初始化数据库

3. **日志位置**:
   - 本地服务日志: `logs/` 目录
   - Docker 服务日志: `docker-compose logs -f [service-name]`

4. **PID 文件**:
   - 本地服务会创建 PID 文件在 `logs/` 目录
   - 停止脚本通过 PID 文件来停止服务

---

## 更新日志

- 2026-01-18: 初始版本，列出所有可执行脚本
