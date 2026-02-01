# Docker Compose 镜像生成说明

## 📋 答案：多个镜像

`docker-compose` 会生成**多个独立的镜像**，每个使用 `build:` 的服务都会生成一个镜像。

---

## 🔍 你的项目中的镜像

### 1. 直接使用现成镜像（不构建）

这些服务使用 `image:` 指令，**不会生成新镜像**，直接拉取现成镜像：

| 服务 | 镜像名称 | 说明 |
|------|---------|------|
| `postgres` | `postgres:16.5-alpine` | PostgreSQL 数据库 |
| `redis` | `redis:7.2-alpine` | Redis 缓存 |
| `zookeeper` | `confluentinc/cp-zookeeper:7.5.3` | Kafka Zookeeper |
| `kafka` | `confluentinc/cp-kafka:7.5.3` | Kafka 消息队列 |

**数量**: 4 个（从 Docker Hub 拉取，不构建）

---

### 2. 需要构建的镜像（使用 `build:`）

这些服务使用 `build:` 指令，**会生成新镜像**：

| 服务 | 构建路径 | 生成的镜像 |
|------|---------|-----------|
| `workflow-engine` | `./backend/workflow-engine-core` | `workflow-station-sun-workflow-engine` |
| `admin-center` | `./backend/admin-center` | `workflow-station-sun-admin-center` |
| `user-portal` | `./backend/user-portal` | `workflow-station-sun-user-portal` |
| `developer-workstation` | `./backend/developer-workstation` | `workflow-station-sun-developer-workstation` |
| `api-gateway` | `./backend/api-gateway` | `workflow-station-sun-api-gateway` |
| `frontend-admin` | `./frontend/admin-center` | `workflow-station-sun-frontend-admin` |
| `frontend-portal` | `./frontend/user-portal` | `workflow-station-sun-frontend-portal` |
| `frontend-developer` | `./frontend/developer-workstation` | `workflow-station-sun-frontend-developer` |

**数量**: 8 个（需要构建）

---

## 📊 总结

| 类型 | 数量 | 说明 |
|------|------|------|
| **现成镜像** | 4 个 | 从 Docker Hub 拉取 |
| **构建镜像** | 8 个 | 使用 Dockerfile 构建 |
| **总计** | **12 个镜像** | 用于运行所有服务 |

---

## 🔄 镜像命名规则

Docker Compose 自动生成的镜像名称格式：

```
<项目目录名>-<服务名>:latest
```

**示例**:
- 项目目录: `Workflow-Station---sun`
- 服务名: `workflow-engine`
- 镜像名: `workflow-station-sun-workflow-engine:latest`

---

## 🎯 实际执行流程

### 执行 `docker-compose up --build`

```bash
# 1. 拉取现成镜像（4个）
docker pull postgres:16.5-alpine
docker pull redis:7.2-alpine
docker pull confluentinc/cp-zookeeper:7.5.3
docker pull confluentinc/cp-kafka:7.5.3

# 2. 构建自定义镜像（8个）
docker build -t workflow-station-sun-workflow-engine ./backend/workflow-engine-core
docker build -t workflow-station-sun-admin-center ./backend/admin-center
docker build -t workflow-station-sun-user-portal ./backend/user-portal
docker build -t workflow-station-sun-developer-workstation ./backend/developer-workstation
docker build -t workflow-station-sun-api-gateway ./backend/api-gateway
docker build -t workflow-station-sun-frontend-admin ./frontend/admin-center
docker build -t workflow-station-sun-frontend-portal ./frontend/user-portal
docker build -t workflow-station-sun-frontend-developer ./frontend/developer-workstation

# 3. 启动容器（12个）
docker run ... postgres:16.5-alpine
docker run ... redis:7.2-alpine
# ... 等等
```

---

## 📦 查看生成的镜像

执行以下命令查看所有镜像：

```bash
# 查看所有镜像
docker images

# 只查看项目相关的镜像
docker images | grep workflow-station-sun

# 查看镜像大小
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
```

---

## 🔍 镜像 vs 容器

**重要区别**:

- **镜像（Image）**: 只读的模板，包含应用和运行环境
- **容器（Container）**: 镜像的运行实例，可以启动/停止/删除

**关系**:
```
镜像（Image） → 容器（Container）
  1个镜像    →  可以运行多个容器
```

**你的项目**:
- **12 个镜像**（4个现成 + 8个构建）
- **12 个容器**（每个服务运行一个容器）

---

## 💡 常见问题

### Q: 可以共享镜像吗？
**A**: 可以！多个服务可以使用同一个镜像，但你的项目中每个服务都有不同的 Dockerfile，所以是独立的镜像。

### Q: 镜像会占用多少空间？
**A**: 
- 现成镜像：约 100-500MB 每个
- 构建镜像：约 200-800MB 每个（包含应用代码）
- 总计：约 3-5GB

### Q: 如何减少镜像数量？
**A**: 
- 使用多阶段构建（Multi-stage build）
- 合并相似的服务
- 使用基础镜像共享层

---

## ✅ 总结

- **docker-compose 生成多个镜像**（不是1个）
- **你的项目**: 4个现成镜像 + 8个构建镜像 = **12个镜像**
- **每个 `build:` 服务 = 1个独立镜像**
- **镜像名称**: 自动生成，格式为 `<项目名>-<服务名>`
