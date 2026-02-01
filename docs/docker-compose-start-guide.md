# Docker Compose 服务启动指南

## 快速启动

### 启动所有服务（Full Profile）

```powershell
# 启动所有服务（后端 + 前端 + 基础设施）
docker-compose --profile full up -d

# 查看服务状态
docker-compose --profile full ps

# 查看日志
docker-compose --profile full logs -f
```

### 只启动后端服务

```powershell
# 启动后端服务 + 基础设施
docker-compose --profile backend up -d

# 查看状态
docker-compose --profile backend ps
```

### 只启动前端服务

```powershell
# 启动前端服务（需要后端已启动）
docker-compose --profile frontend up -d
```

## 服务端口映射

启动后，服务可以通过以下端口访问：

| 服务 | 端口 | 访问地址 |
|------|------|---------|
| API Gateway | 8080 | http://localhost:8080 |
| Workflow Engine | 8081 | http://localhost:8081 |
| User Portal Backend | 8082 | http://localhost:8082 |
| Developer Workstation Backend | 8083 | http://localhost:8083 |
| Admin Center Backend | 8090 | http://localhost:8090 |
| Frontend Admin | 3000 | http://localhost:3000 |
| Frontend Portal | 3001 | http://localhost:3001 |
| Frontend Developer | 3002 | http://localhost:3002 |
| PostgreSQL | 5432 | localhost:5432 |
| Redis | 6379 | localhost:6379 |
| Kafka | 9092 | localhost:9092 |

## 启动步骤详解

### 步骤 1：检查镜像是否已构建

```powershell
# 查看所有镜像
docker images | Select-String workflow-platform

# 应该看到 8 个镜像：
# - workflow-platform/api-gateway
# - workflow-platform/workflow-engine
# - workflow-platform/admin-center
# - workflow-platform/developer-workstation
# - workflow-platform/user-portal
# - workflow-platform/frontend-admin
# - workflow-platform/frontend-developer
# - workflow-platform/frontend-portal
```

### 步骤 2：启动服务

```powershell
# 方式 1：启动所有服务（推荐）
docker-compose --profile full up -d

# 方式 2：分步启动
# 先启动基础设施
docker-compose up -d postgres redis zookeeper kafka

# 等待基础设施就绪后，启动后端
docker-compose --profile backend up -d

# 最后启动前端
docker-compose --profile frontend up -d
```

### 步骤 3：检查服务状态

```powershell
# 查看所有服务状态
docker-compose --profile full ps

# 应该看到所有服务都是 "Up" 状态
```

### 步骤 4：查看日志

```powershell
# 查看所有服务日志
docker-compose --profile full logs -f

# 查看特定服务日志
docker-compose logs -f admin-center
docker-compose logs -f frontend-admin

# 查看最近 100 行日志
docker-compose logs --tail=100 admin-center
```

## 常见启动问题

### 问题 1：端口已被占用

**错误信息：**
```
Error: bind: address already in use
```

**解决方案：**
```powershell
# 检查端口占用
netstat -ano | findstr :8080

# 停止占用端口的服务，或修改 docker-compose.yml 中的端口映射
```

### 问题 2：服务启动失败

**查看日志：**
```powershell
# 查看失败服务的日志
docker-compose logs service-name

# 查看所有服务状态
docker-compose ps
```

### 问题 3：数据库连接失败

**等待数据库就绪：**
```powershell
# 检查数据库是否就绪
docker-compose exec postgres pg_isready -U platform

# 如果数据库未就绪，等待一段时间后重试
```

### 问题 4：服务依赖问题

**按顺序启动：**
```powershell
# 1. 先启动基础设施
docker-compose up -d postgres redis

# 2. 等待健康检查通过
docker-compose ps

# 3. 启动后端服务
docker-compose --profile backend up -d

# 4. 启动前端服务
docker-compose --profile frontend up -d
```

## 服务管理命令

### 启动服务

```powershell
# 启动所有服务
docker-compose --profile full up -d

# 启动并重建镜像
docker-compose --profile full up -d --build

# 启动并强制重新创建容器
docker-compose --profile full up -d --force-recreate
```

### 停止服务

```powershell
# 停止所有服务
docker-compose --profile full down

# 停止并删除数据卷（⚠️ 会删除数据）
docker-compose --profile full down -v

# 停止特定服务
docker-compose stop admin-center
```

### 重启服务

```powershell
# 重启所有服务
docker-compose --profile full restart

# 重启特定服务
docker-compose restart admin-center
```

### 查看服务状态

```powershell
# 查看所有服务状态
docker-compose --profile full ps

# 查看服务详细信息
docker-compose ps -a
```

### 查看日志

```powershell
# 实时查看所有日志
docker-compose --profile full logs -f

# 查看特定服务日志
docker-compose logs -f admin-center

# 查看最近日志
docker-compose logs --tail=50 admin-center
```

## 健康检查

### 检查服务健康状态

```powershell
# 检查所有服务
docker-compose --profile full ps

# 应该看到所有服务都是 "Up (healthy)" 或 "Up"
```

### 手动健康检查

```powershell
# 检查后端服务
curl http://localhost:8090/api/v1/admin/actuator/health

# 检查前端服务
curl http://localhost:3000
```

## 完整启动流程

```powershell
# 1. 确保镜像已构建
docker images | Select-String workflow-platform

# 2. 启动所有服务
docker-compose --profile full up -d

# 3. 等待服务启动（约 1-2 分钟）
Start-Sleep -Seconds 60

# 4. 检查服务状态
docker-compose --profile full ps

# 5. 查看日志（如果有问题）
docker-compose --profile full logs --tail=50

# 6. 访问服务
# - 前端: http://localhost:3000
# - 后端 API: http://localhost:8090
```

## 访问服务

启动成功后，可以通过以下地址访问：

- **Admin Center 前端**: http://localhost:3000
- **User Portal 前端**: http://localhost:3001
- **Developer Workstation 前端**: http://localhost:3002
- **Admin Center API**: http://localhost:8090/api/v1/admin
- **API Gateway**: http://localhost:8080

## 停止服务

```powershell
# 停止所有服务
docker-compose --profile full down

# 停止并删除数据卷（⚠️ 会删除数据库数据）
docker-compose --profile full down -v
```

## 故障排查

如果服务启动失败：

1. **查看日志**
   ```powershell
   docker-compose logs service-name
   ```

2. **检查服务状态**
   ```powershell
   docker-compose ps
   ```

3. **检查端口占用**
   ```powershell
   netstat -ano | findstr :8080
   ```

4. **重启服务**
   ```powershell
   docker-compose restart service-name
   ```

5. **重新构建并启动**
   ```powershell
   docker-compose --profile full up -d --build
   ```
