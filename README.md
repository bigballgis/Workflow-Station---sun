# Workflow Platform

企业级低代码工作流平台，提供可视化流程设计、工作流自动化和业务流程管理能力。

## 目录

- [架构概览](#架构概览)
- [技术栈](#技术栈)
- [模块说明](#模块说明)
- [快速开始](#快速开始)
- [默认账号](#默认账号)
- [配置说明](#配置说明)
- [API 文档](#api-文档)
- [开发指南](#开发指南)
- [部署](#部署)
- [文档](#文档)

## 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway (8080)                       │
│                    (Spring Cloud Gateway)                       │
└─────────────────────────┬───────────────────────────────────────┘
                          │
    ┌─────────────────────┼─────────────────────┐
    │                     │                     │
    ▼                     ▼                     ▼
┌─────────┐        ┌─────────────┐       ┌──────────┐
│ Admin   │        │  Workflow   │       │  User    │
│ Center  │        │   Engine    │       │  Portal  │
│ (8090)  │        │   (8081)    │       │  (8082)  │
└────┬────┘        └──────┬──────┘       └────┬─────┘
     │                    │                   │
     │              ┌─────┴─────┐             │
     │              │Developer  │             │
     │              │Workstation│             │
     │              │  (8083)   │             │
     │              └─────┬─────┘             │
     │                    │                   │
     └────────────────────┼───────────────────┘
                          │
              ┌───────────┴───────────┐
              │                       │
         ┌────▼────┐            ┌─────▼─────┐
         │  Redis  │            │PostgreSQL │
         │  (6379) │            │   (5432)  │
         └─────────┘            └───────────┘
```

## 技术栈

| 层级 | 技术 |
|-------|------------|
| 后端框架 | Java 17, Spring Boot 3.2, Spring Cloud Gateway |
| 前端框架 | Vue 3, TypeScript, Element Plus, Vite |
| 数据库 | PostgreSQL 16.5 |
| 缓存 | Redis 7.2 |
| 工作流引擎 | Flowable 7.0.0 |
| 表单设计 | form-create, bpmn-js |
| 容器化 | Docker, Docker Compose |
| 构建工具 | Maven 3.9+, pnpm 10.28+ |

## 模块说明

### 后端服务

| 模块 | 端口 | 说明 |
|------|------|------|
| `api-gateway` | 8080 | API 网关，统一入口 |
| `workflow-engine-core` | 8081 | 工作流引擎（基于 Flowable） |
| `user-portal` | 8082 | 用户门户后端 |
| `developer-workstation` | 8083 | 开发者工作站后端 |
| `admin-center` | 8090 | 管理中心后端 |

### 共享库

| 模块 | 说明 |
|------|------|
| `platform-common` | 共享 DTO、异常、工具类 |
| `platform-security` | JWT 认证、加密服务 |
| `platform-cache` | Redis 缓存服务 |
| `platform-messaging` | 消息发布服务 |

### 前端应用

| 应用 | 端口 | 说明 |
|------|------|------|
| `frontend/admin-center` | 3000 | 管理中心 UI（用户、角色、权限管理） |
| `frontend/developer-workstation` | 3002 | 开发者工作站 UI（流程设计、表单设计） |
| `frontend/user-portal` | 3001 | 用户门户 UI（任务处理、流程发起） |

## 快速开始

### 前置条件

- Java 17+
- Node.js 18+
- Maven 3.9+
- Docker & Docker Compose
- PowerShell（Windows）或 Bash（macOS/Linux）

### 本地开发（推荐）

#### 1. 启动基础设施服务

```powershell
# 启动 PostgreSQL 和 Redis
docker-compose up -d postgres redis

# 验证服务状态
docker ps
```

#### 2. 启动后端服务

在不同的终端窗口中分别执行：

```powershell
# Admin Center (端口 8090)
mvn spring-boot:run -pl backend/admin-center -DskipTests

# User Portal (端口 8082)
mvn spring-boot:run -pl backend/user-portal -DskipTests

# Developer Workstation (端口 8083)
mvn spring-boot:run -pl backend/developer-workstation -DskipTests

# Workflow Engine (端口 8081)
mvn spring-boot:run -pl backend/workflow-engine-core -DskipTests

# API Gateway (端口 8080) - 可选
mvn spring-boot:run -pl backend/api-gateway -DskipTests
```

#### 3. 启动前端服务

在不同的终端窗口中分别执行：

```powershell
# Admin Center Frontend (端口 3000)
cd frontend/admin-center
npm install  # 首次运行
npm run dev

# User Portal Frontend (端口 3001)
cd frontend/user-portal
npm install  # 首次运行
npm run dev

# Developer Workstation Frontend (端口 3002)
cd frontend/developer-workstation
npm install  # 首次运行
npm run dev
```

#### 4. 访问应用

- Admin Center: http://localhost:3000
- User Portal: http://localhost:3001
- Developer Workstation: http://localhost:3002

### 完整 Docker 部署

```powershell
# 启动所有服务
docker-compose --profile full up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

## 配置说明

## API 文档

Swagger API 文档地址：

| 服务 | 地址 |
|------|------|
| Admin Center | http://localhost:8090/swagger-ui.html |
| User Portal | http://localhost:8082/swagger-ui.html |
| Developer Workstation | http://localhost:8083/swagger-ui.html |
| Workflow Engine | http://localhost:8081/swagger-ui.html |
| API Gateway | http://localhost:8080/swagger-ui.html |

## 开发指南

### 代码规范

- 后端遵循 Spring Boot 最佳实践
- 前端遵循 Vue 3 Composition API 规范
- 使用 TypeScript 进行类型检查
- 遵循 RESTful API 设计原则

### 数据库迁移

使用 Flyway 管理数据库版本：

```powershell
# 查看迁移状态
mvn flyway:info -pl backend/platform-security

# 执行迁移
mvn flyway:migrate -pl backend/platform-security
```

### 测试

```powershell
# 运行所有测试
mvn test

# 运行单个模块测试
mvn test -pl backend/admin-center

# 生成测试覆盖率报告
mvn test jacoco:report
```

### 常用命令

```powershell
# 清理构建
mvn clean

# 编译项目
mvn compile

# 打包（跳过测试）
mvn package -DskipTests

# 查看依赖树
mvn dependency:tree
```

### 数据库操作

```powershell
# 连接数据库
docker exec -it platform-postgres psql -U platform -d workflow_platform

# 执行 SQL 文件
Get-Content -Path "script.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform

# 导出数据
docker exec -i platform-postgres pg_dump -U platform workflow_platform > backup.sql
```

## 部署

### Docker Compose 部署

```powershell
# 生产环境部署
docker-compose -f docker-compose.prod.yml up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f [service-name]

# 停止服务
docker-compose down
```

### 环境变量配置

生产环境需要配置以下环境变量：

```bash
# 数据库
POSTGRES_PASSWORD=<strong-password>
POSTGRES_HOST=<db-host>

# Redis
REDIS_PASSWORD=<strong-password>
REDIS_HOST=<redis-host>

# JWT
JWT_SECRET_KEY=<random-secret-key>

# 加密
ENCRYPTION_KEY=<random-encryption-key>
```

## 文档

### 项目文档

- [启动指南](docs/startup-guide.md) - 详细的服务启动说明
- [需求文档](docs/requirements-full/) - 完整的需求规格说明
- [架构设计](docs/architecture-diagrams.md) - 系统架构图
- [数据库设计](docs/database-refactor-plan.md) - 数据库设计文档

### 开发指南

- [开发细则](.kiro/steering/development-guidelines.md) - 开发规范和最佳实践
- [工作流引擎](.kiro/steering/workflow-engine-architecture.md) - 工作流引擎架构
- [功能单元生成](.kiro/steering/function-unit-generation.md) - 功能单元 SQL 生成指南
- [多语言支持](.kiro/steering/i18n-guidelines.md) - 国际化开发指南

### API 文档

- [Admin Center API](http://localhost:8090/swagger-ui.html)
- [User Portal API](http://localhost:8082/swagger-ui.html)
- [Developer Workstation API](http://localhost:8083/swagger-ui.html)
- [Workflow Engine API](http://localhost:8081/swagger-ui.html)

## 故障排查

### 常见问题

1. **端口被占用**
   ```powershell
   # 查找占用端口的进程
   netstat -ano | findstr ":8080"
   # 终止进程
   taskkill /PID <进程ID> /F
   ```

2. **数据库连接失败**
   - 检查 PostgreSQL 容器是否运行
   - 验证数据库密码配置
   - 检查防火墙设置

3. **前端代理错误**
   - 确保后端服务已启动
   - 检查 `vite.config.ts` 中的代理配置
   - 清除浏览器缓存

4. **Maven 构建失败**
   - 清理本地仓库：`mvn clean`
   - 更新依赖：`mvn dependency:resolve`
   - 检查 Java 版本：`java -version`

### 日志查看

```powershell
# Docker 服务日志
docker logs -f platform-postgres
docker logs -f platform-redis

# 后端服务日志
# 日志文件位置：logs/[service-name].log
tail -f logs/admin-center.log
```

## 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目为内部使用项目，版权所有。

## 联系方式

如有问题或建议，请联系项目维护团队。

---

**最后更新**: 2026-01-28
