# 使用云数据库（Azure Database）配置指南

生成时间: 2026-01-18

本指南说明如何使用 Azure Database for PostgreSQL（或其他云数据库）替代本地 PostgreSQL 服务。

---

## 📋 目录

1. [概述](#概述)
2. [为什么不需要启动本地 PostgreSQL](#为什么不需要启动本地-postgresql)
3. [配置步骤](#配置步骤)
4. [启动服务（跳过 PostgreSQL）](#启动服务跳过-postgresql)
5. [验证配置](#验证配置)
6. [常见问题](#常见问题)

---

## 概述

当您使用云数据库（如 Azure Database for PostgreSQL、AWS RDS、阿里云 RDS 等）时：

- ✅ **不需要**在本地启动 PostgreSQL Docker 容器
- ✅ **不需要**安装本地 PostgreSQL
- ✅ 只需要启动其他基础设施服务（Redis、Kafka 等）
- ✅ 应用直接连接到云数据库

---

## 为什么不需要启动本地 PostgreSQL

### 云数据库的优势

1. **已提供 PostgreSQL 服务**
   - Azure Database for PostgreSQL 已经是一个运行中的 PostgreSQL 实例
   - 不需要在本地再运行一个 PostgreSQL

2. **网络连接**
   - 应用通过互联网连接到云数据库
   - 不需要本地 Docker 网络

3. **资源节省**
   - 不占用本地内存和 CPU
   - 不需要管理本地数据库容器

---

## 配置步骤

### 步骤 1: 在 DBeaver 中连接云数据库

参考 [数据库同步指南](./database-sync-guide.md) 中的"方法 1: 使用 DBeaver 连接云数据库"部分。

### 步骤 2: 初始化数据库结构

在 DBeaver 中执行所有 Flyway 迁移脚本，创建表结构。

### 步骤 3: 更新应用配置

修改所有后端模块的 `application.yml`，将数据库连接指向云数据库：

**文件位置**:
- `backend/api-gateway/src/main/resources/application.yml`
- `backend/admin-center/src/main/resources/application.yml`
- `backend/user-portal/src/main/resources/application.yml`
- `backend/workflow-engine-core/src/main/resources/application.yml`
- `backend/developer-workstation/src/main/resources/application.yml`

**Azure Database 配置示例**:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://your-server.postgres.database.azure.com:5432/workflow_platform?sslmode=require
    username: your_username@your-server
    password: your_password
    driver-class-name: org.postgresql.Driver
```

**使用环境变量（推荐）**:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform}
    username: ${SPRING_DATASOURCE_USERNAME:platform}
    password: ${SPRING_DATASOURCE_PASSWORD:platform123}
```

然后在 `.env` 文件或系统环境变量中设置：

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://your-server.postgres.database.azure.com:5432/workflow_platform?sslmode=require
SPRING_DATASOURCE_USERNAME=your_username@your-server
SPRING_DATASOURCE_PASSWORD=your_password
```

### 步骤 4: 配置 Azure 防火墙规则

在 Azure 门户中：

1. 打开您的 Azure Database for PostgreSQL 实例
2. 进入"连接安全性"或"防火墙规则"
3. 添加以下规则：
   - **允许 Azure 服务访问**: 启用（如果应用也在 Azure 上）
   - **添加客户端 IP**: 添加您的开发机器 IP 地址
   - **或者添加 IP 范围**: 如果使用 VPN 或固定 IP

---

## 启动服务（跳过 PostgreSQL）

### 使用 Docker Compose

**只启动需要的服务**:

```powershell
# 只启动 Redis 和 Kafka（不启动 PostgreSQL）
docker-compose up -d redis zookeeper kafka
```

**验证服务状态**:

```powershell
docker-compose ps
```

应该看到：
- ✅ `platform-redis` - 运行中
- ✅ `platform-zookeeper` - 运行中
- ✅ `platform-kafka` - 运行中
- ❌ **不应该**看到 `platform-postgres`（因为使用云数据库）

### 启动后端服务

后端服务会直接连接到云数据库，不需要等待本地 PostgreSQL：

```powershell
# 方法 1: 使用 Docker Compose（如果使用 Docker 部署后端）
docker-compose --profile backend up -d

# 方法 2: 本地开发模式（每个服务单独启动）
cd backend/api-gateway
mvn spring-boot:run

# 其他终端窗口启动其他服务...
```

---

## 验证配置

### 1. 验证云数据库连接

**在 DBeaver 中**:
```sql
-- 测试连接
SELECT version();

-- 检查表是否存在
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;
```

**使用命令行**（如果安装了 PostgreSQL 客户端）:
```powershell
psql -h your-server.postgres.database.azure.com -U your_username@your-server -d workflow_platform
```

### 2. 验证应用连接

启动后端服务后，检查日志：

```powershell
# 查看服务日志
docker-compose logs api-gateway
# 或
# 查看本地启动的服务日志（在 logs/ 目录）
```

应该看到：
- ✅ "Connected to database" 或类似的成功消息
- ❌ **不应该**看到 "Connection refused" 或 "Connection timeout"

### 3. 测试 API

访问 Swagger 文档：
- http://localhost:8080/swagger-ui.html (API Gateway)
- http://localhost:8090/swagger-ui.html (Admin Center)

如果 API 可以正常访问，说明数据库连接成功。

---

## 常见问题

### Q1: 启动后端服务时出现 "Connection refused" 错误

**原因**: 应用无法连接到云数据库

**解决方案**:
1. 检查 Azure 防火墙规则，确保允许您的 IP 地址
2. 检查数据库连接配置（URL、用户名、密码）
3. 检查网络连接：
   ```powershell
   Test-NetConnection -ComputerName your-server.postgres.database.azure.com -Port 5432
   ```

### Q2: 启动后端服务时出现 SSL 错误

**原因**: Azure Database 要求 SSL 连接

**解决方案**:
在 JDBC URL 中添加 SSL 参数：
```yaml
url: jdbc:postgresql://your-server.postgres.database.azure.com:5432/workflow_platform?sslmode=require
```

### Q3: Docker Compose 启动时仍然尝试启动 PostgreSQL

**原因**: `docker-compose up` 默认会启动所有服务

**解决方案**:
明确指定要启动的服务：
```powershell
# 只启动需要的服务
docker-compose up -d redis zookeeper kafka

# 不要使用
# docker-compose up -d  # 这会启动所有服务，包括 postgres
```

### Q4: 后端服务依赖检查失败（depends_on postgres）

**原因**: 如果使用 Docker Compose 部署后端服务，它们可能依赖 `postgres` 服务

**解决方案**:
1. **选项 1**: 修改 `docker-compose.yml`，移除后端服务对 `postgres` 的依赖
2. **选项 2**: 使用本地开发模式启动后端服务（`mvn spring-boot:run`），不依赖 Docker Compose

### Q5: 如何知道应用连接的是云数据库还是本地数据库？

**检查方法**:
1. 查看应用日志中的数据库连接信息
2. 在 DBeaver 中查看云数据库的连接数（应该会增加）
3. 停止本地 PostgreSQL（如果运行了），应用应该仍然可以工作

---

## 快速检查清单

使用云数据库时：

- [ ] 已在 DBeaver 中成功连接到云数据库
- [ ] 已在 DBeaver 中执行所有 SQL 迁移脚本
- [ ] 已更新所有 `application.yml` 中的数据库连接配置
- [ ] 已配置 Azure 防火墙规则（允许应用访问）
- [ ] 已启动 Redis 和 Kafka（不启动 PostgreSQL）
- [ ] 后端服务可以成功启动并连接到云数据库
- [ ] API 可以正常访问（测试 Swagger）

---

## 总结

使用云数据库时：

1. ✅ **不需要**启动本地 PostgreSQL
2. ✅ 只需要启动 Redis 和 Kafka
3. ✅ 应用直接连接到云数据库
4. ✅ 节省本地资源，更简单高效

---

**最后更新**: 2026-01-18
