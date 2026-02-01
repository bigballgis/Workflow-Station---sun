# Start-All 脚本修复总结

## 概述

已完成对 `start-all.ps1` 和 `start-all.sh` 脚本的修复，解决了与标准化 application.yml 文件的兼容性问题。

## 主要修复内容

### 1. 移除 SPRING_PROFILES_ACTIVE=docker

**问题**: 脚本设置了 `SPRING_PROFILES_ACTIVE=docker`，但标准化后的 application.yml 文件不再使用 profile 配置。

**修复**: 移除所有 `SPRING_PROFILES_ACTIVE=docker` 环境变量设置。

### 2. 修正容器名称引用

**问题**: 环境变量中使用了错误的容器名称（如 `postgres`, `redis`），实际容器名称是 `platform-postgres`, `platform-redis`。

**修复**: 更新所有环境变量中的容器名称：
- `postgres` → `platform-postgres`
- `redis` → `platform-redis`
- `admin-center` → `platform-admin-center`
- `workflow-engine` → `platform-workflow-engine`

### 3. 添加数据库 Schema 参数

**问题**: 数据库连接 URL 缺少 `currentSchema=projectx` 参数。

**修复**: 所有数据库连接 URL 更新为：
```
jdbc:postgresql://platform-postgres:5432/workflow_platform?currentSchema=projectx
```

### 4. 修正端口映射

**问题**: 容器内部端口与外部端口映射不一致。

**修复**: 更新端口映射以匹配标准化配置：
- Workflow Engine: `8091:8091` (之前是 `8091:8080`)
- Admin Center: `8092:8092` (之前是 `8092:8080`)
- User Portal: `8093:8093` (之前是 `8093:8080`)
- Developer Workstation: `8094:8094` (之前是 `8094:8080`)
- API Gateway: `8090:8090` (之前是 `8090:8080`)

### 5. 更新服务间通信 URL

**修复**: 更新服务间通信的 URL，包含正确的 context path：
- Admin Center URL: `http://platform-admin-center:8092/api/v1/admin`
- User Portal URL: `http://platform-user-portal:8093/api/portal`
- Workflow Engine URL: `http://platform-workflow-engine:8091`

### 6. 移除 Kafka 和 Zookeeper

**问题**: 项目只需要 PostgreSQL 和 Redis 作为基础设施。

**修复**: 
- 移除 Kafka 和 Zookeeper 容器启动代码
- 移除所有 `SPRING_KAFKA_BOOTSTRAP_SERVERS` 环境变量
- 更新基础设施服务列表

### 7. 修复 PowerShell 跨平台兼容性

**问题**: PowerShell 脚本在 macOS 上无法运行，因为包含 Windows 特定的 `chcp` 命令。

**修复**: 
- 添加平台检测，只在 Windows 上执行 `chcp` 命令
- 创建等效的 shell 脚本 (`start-all.sh`) 供 macOS/Linux 使用

## 修复后的服务配置

### 基础设施服务
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379

### 后端服务
- **API Gateway**: http://localhost:8090
- **Workflow Engine**: http://localhost:8091
- **Admin Center**: http://localhost:8092
- **User Portal**: http://localhost:8093
- **Developer Workstation**: http://localhost:8094

### 前端应用
- **Admin Center**: http://localhost:3000
- **User Portal**: http://localhost:3001
- **Developer Workstation**: http://localhost:3002

## 使用方法

### macOS/Linux (推荐)
```bash
# 启动所有服务
./start-all.sh

# 只启动基础设施
./start-all.sh --infra-only

# 只启动后端服务
./start-all.sh --backend-only

# 只启动前端服务
./start-all.sh --frontend-only

# 跳过构建
./start-all.sh --no-build
```

### Windows (需要 PowerShell Core)
```powershell
# 启动所有服务
.\start-all.ps1

# 只启动基础设施
.\start-all.ps1 -InfraOnly

# 只启动后端服务
.\start-all.ps1 -BackendOnly

# 只启动前端服务
.\start-all.ps1 -FrontendOnly

# 跳过构建
.\start-all.ps1 -NoBuild
```

## 验证清单

- [x] 移除 `SPRING_PROFILES_ACTIVE=docker` 环境变量
- [x] 修正所有容器名称引用
- [x] 添加 `currentSchema=projectx` 到数据库 URL
- [x] 修正端口映射匹配 application.yml 配置
- [x] 更新服务间通信 URL 包含正确的 context path
- [x] 移除 Kafka 和 Zookeeper 相关配置
- [x] 修复 PowerShell 跨平台兼容性
- [x] 创建等效的 shell 脚本供 macOS/Linux 使用
- [x] 更新服务 URL 显示信息

## 相关文件

- `start-all.ps1` - Windows PowerShell 脚本
- `start-all.sh` - macOS/Linux shell 脚本
- `docs/APPLICATION_YML_STANDARDIZATION.md` - 应用配置标准化文档

## 注意事项

1. **环境变量优先级**: Docker 环境变量会覆盖 application.yml 中的默认值
2. **网络连接**: 所有容器都在 `platform-network` 网络中，使用容器名称进行通信
3. **数据持久化**: 使用 Docker volumes 持久化 PostgreSQL 和 Redis 数据
4. **日志查看**: 使用 `docker logs -f [container-name]` 查看服务日志
5. **停止服务**: 使用对应的 `stop-all` 脚本停止所有服务