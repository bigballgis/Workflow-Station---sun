---
inclusion: always
---

# 服务启动快速参考

详细文档见：#[[file:docs/startup-guide.md]]

## 自动重启规则（重要！）

**每次后端 Java 代码有修改，任务完成后必须：**

1. **自动重启相关的后端服务**
   - 修改了 `backend/admin-center` → 重启 admin-center 服务
   - 修改了 `backend/user-portal` → 重启 user-portal 服务
   - 修改了 `backend/developer-workstation` → 重启 developer-workstation 服务
   - 修改了 `backend/workflow-engine-core` → 重启 workflow-engine-core 服务
   - 修改了 `backend/api-gateway` → 重启 api-gateway 服务
   - 修改了 `backend/platform-common` 或 `backend/platform-security` → 重启所有依赖它的服务

2. **检查启动日志**
   - 确认服务成功启动（看到 "Started XXXApplication" 日志）
   - 如果启动失败，自动分析错误并修复

3. **自动修复流程**
   - 编译错误：检查代码语法、缺失的导入、类型不匹配
   - 数据库错误：检查 Flyway 迁移文件、Entity 字段与数据库列是否匹配
   - Bean 注入错误：检查 @Autowired、@Component 等注解
   - 修复后重新启动服务

### 重启命令模板
```powershell
# 停止进程（使用 controlPwshProcess stop）
# 启动进程
mvn spring-boot:run -pl backend/{module-name} -DskipTests
```

### 服务模块映射
| 模块路径 | 服务名 | 端口 |
|---------|--------|------|
| backend/admin-center | admin-center | 8090 |
| backend/user-portal | user-portal | 8082 |
| backend/developer-workstation | developer-workstation | 8083 |
| backend/workflow-engine-core | workflow-engine-core | 8081 |
| backend/api-gateway | api-gateway | 8080 |

## 服务端口

| 服务 | 端口 |
|------|------|
| PostgreSQL | 5432 |
| Redis | 6379 |
| API Gateway | 8080 |
| Workflow Engine Core | 8081 |
| User Portal Backend | 8082 |
| Developer Workstation Backend | 8083 |
| Admin Center Backend | 8090 |
| Admin Center Frontend | 3000 |
| User Portal Frontend | 3001 |
| Developer Workstation Frontend | 3002 |

## 快速启动命令

### 基础设施（Docker）
```powershell
docker-compose up -d postgres redis
```

### 后端服务（本地）
```powershell
mvn spring-boot:run -pl backend/admin-center -DskipTests
mvn spring-boot:run -pl backend/user-portal -DskipTests
mvn spring-boot:run -pl backend/developer-workstation -DskipTests
mvn spring-boot:run -pl backend/workflow-engine-core -DskipTests
mvn spring-boot:run -pl backend/api-gateway -DskipTests
```

### 前端服务（本地）
```powershell
cd frontend/admin-center && npm run dev
cd frontend/user-portal && npm run dev
cd frontend/developer-workstation && npm run dev
```

## 数据库配置
- 数据库：`workflow_platform`
- 用户名：`platform`
- 密码：`platform123`
- Redis 密码：`redis123`
