# Docker 依赖管理指南

## 📌 核心概念

**`docker build` 不能管理运行时依赖！**

- `docker build` 只负责**构建镜像**
- 依赖管理发生在**运行时**（容器启动时）

## 🔄 依赖管理的三种方式

### 方式 1: 手动管理（不推荐）

```bash
# 1. 启动数据库
docker run -d --name postgres postgres:16.5-alpine

# 2. 启动 Redis
docker run -d --name redis redis:7.2-alpine

# 3. 创建网络
docker network create my-network
docker network connect my-network postgres
docker network connect my-network redis

# 4. 构建应用
docker build -t my-app .

# 5. 运行应用（手动传递环境变量）
docker run -d --name app \
  --network my-network \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/db \
  -e SPRING_REDIS_HOST=redis \
  my-app
```

**问题**: 繁琐、易错、难以维护

---

### 方式 2: docker-compose（✅ 推荐）

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16.5-alpine
    environment:
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  
  redis:
    image: redis:7.2-alpine
  
  app:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/db
      SPRING_REDIS_HOST: redis
    depends_on:
      - postgres
      - redis
```

**优势**: 一键启动、自动网络、自动依赖

---

### 方式 3: Dockerfile 中安装依赖（❌ 不推荐）

```dockerfile
# ❌ 错误做法
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache postgresql  # 不推荐！
```

**问题**: 违反单一职责、镜像臃肿、难以扩展

---

## 🏗️ 你的项目中的实际流程

### 1. Dockerfile（构建阶段）

```dockerfile
# 只负责构建应用镜像
FROM eclipse-temurin:17-jre-alpine
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**作用**: 创建包含应用的镜像

### 2. application.yml（配置阶段）

```yaml
# 通过环境变量读取配置
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/db}
  data:
    redis:
      host: ${SPRING_REDIS_HOST:localhost}
```

**作用**: 定义如何连接依赖（通过环境变量）

### 3. docker-compose.yml（运行阶段）

```yaml
services:
  workflow-engine:
    build:
      dockerfile: Dockerfile  # 使用 Dockerfile 构建
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/workflow_platform
      SPRING_REDIS_HOST: redis  # 运行时连接
    depends_on:
      - postgres  # 运行时依赖
      - redis
```

**作用**: 在运行时提供依赖服务和环境变量

---

## 🔗 依赖连接原理

### 1. 网络连接

```yaml
# docker-compose 自动创建网络
networks:
  platform-network:
    driver: bridge

# 服务可以通过服务名访问
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/db
#                                    ^^^^^^^^
#                                    服务名（不是 localhost！）
```

### 2. 环境变量传递

```yaml
# docker-compose 传递环境变量
environment:
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/db

# 应用读取
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}  # 从环境变量读取
```

### 3. 依赖顺序

```yaml
depends_on:
  postgres:
    condition: service_healthy  # 等待数据库就绪
  redis:
    condition: service_healthy  # 等待 Redis 就绪
```

---

## 📊 对比表

| 操作 | 作用 | 能否管理依赖 |
|------|------|------------|
| `docker build` | 构建镜像 | ❌ 不能 |
| `docker run` | 运行容器 | ⚠️ 可以，需手动 |
| `docker-compose up` | 编排服务 | ✅ 可以，自动 |

---

## ✅ 最佳实践

1. ✅ **使用 Dockerfile 构建应用镜像**
2. ✅ **使用 docker-compose 管理所有服务**
3. ✅ **通过环境变量配置依赖连接**
4. ✅ **使用服务名进行容器间通信**
5. ❌ **不要在 Dockerfile 中安装依赖服务**

---

## 🚀 快速开始

```bash
# 一键启动所有服务（包括依赖）
docker-compose up

# 这会：
# 1. 启动 postgres
# 2. 启动 redis
# 3. 使用 Dockerfile 构建 workflow-engine
# 4. 启动 workflow-engine（自动连接 postgres 和 redis）
```
