# Docker 镜像打包指南

本文档介绍如何将 Workflow Platform 项目打包成 Docker 镜像。

## 项目结构

项目包含以下服务：

### 后端服务（Java/Spring Boot）
- `api-gateway` - API 网关
- `workflow-engine-core` - 工作流引擎核心
- `admin-center` - 管理后台
- `developer-workstation` - 开发者工作站
- `user-portal` - 用户门户

### 前端服务（Vue.js）
- `frontend/admin-center` - 管理后台前端
- `frontend/developer-workstation` - 开发者工作站前端
- `frontend/user-portal` - 用户门户前端

## 方法一：使用 Docker Compose 构建（推荐）

### 构建所有服务

```bash
# 构建所有后端和前端服务
docker-compose build

# 或者只构建特定服务
docker-compose build admin-center
docker-compose build frontend-admin
```

### 构建并启动所有服务

```bash
# 构建并启动所有服务（包括基础设施）
docker-compose --profile full up --build -d

# 只启动后端服务
docker-compose --profile backend up --build -d

# 只启动前端服务
docker-compose --profile frontend up --build -d
```

## 方法二：单独构建每个服务

### 构建后端服务

```bash
# 进入后端服务目录
cd backend/admin-center

# 构建镜像
docker build -t workflow-platform/admin-center:latest .

# 或者指定版本
docker build -t workflow-platform/admin-center:1.0.0 .
```

### 构建前端服务

```bash
# 进入前端服务目录
cd frontend/admin-center

# 构建镜像
docker build -t workflow-platform/frontend-admin:latest .
```

## 方法三：批量构建脚本

### 创建构建脚本

创建 `build-all-images.sh`：

```bash
#!/bin/bash

# 设置镜像标签
VERSION=${1:-latest}
REGISTRY=${2:-workflow-platform}

echo "Building all images with version: $VERSION"

# 构建后端服务
echo "Building backend services..."
docker build -t $REGISTRY/api-gateway:$VERSION ./backend/api-gateway
docker build -t $REGISTRY/workflow-engine:$VERSION ./backend/workflow-engine-core
docker build -t $REGISTRY/admin-center:$VERSION ./backend/admin-center
docker build -t $REGISTRY/developer-workstation:$VERSION ./backend/developer-workstation
docker build -t $REGISTRY/user-portal:$VERSION ./backend/user-portal

# 构建前端服务
echo "Building frontend services..."
docker build -t $REGISTRY/frontend-admin:$VERSION ./frontend/admin-center
docker build -t $REGISTRY/frontend-developer:$VERSION ./frontend/developer-workstation
docker build -t $REGISTRY/frontend-portal:$VERSION ./frontend/user-portal

echo "All images built successfully!"
echo ""
echo "To view all images:"
echo "  docker images | grep $REGISTRY"
```

### 使用构建脚本

```bash
# 赋予执行权限
chmod +x build-all-images.sh

# 构建所有镜像（使用 latest 标签）
./build-all-images.sh

# 构建所有镜像（指定版本）
./build-all-images.sh 1.0.0

# 构建并推送到私有仓库
./build-all-images.sh 1.0.0 your-registry.com/workflow-platform
```

## 方法四：使用 Maven 和 Docker 多阶段构建

### 优化构建速度

如果项目很大，可以使用 Maven 缓存来加速构建：

```bash
# 先构建 Maven 依赖（只构建一次）
docker build --target maven-deps -t workflow-platform/maven-deps:latest .

# 然后构建各个服务（使用缓存的依赖）
docker build --cache-from workflow-platform/maven-deps:latest -t workflow-platform/admin-center:latest ./backend/admin-center
```

## 推送镜像到仓库

### 推送到 Docker Hub

```bash
# 登录 Docker Hub
docker login

# 标记镜像
docker tag workflow-platform/admin-center:latest your-username/workflow-platform-admin-center:latest

# 推送镜像
docker push your-username/workflow-platform-admin-center:latest
```

### 推送到私有仓库

```bash
# 标记镜像
docker tag workflow-platform/admin-center:latest registry.example.com/workflow-platform/admin-center:1.0.0

# 推送镜像
docker push registry.example.com/workflow-platform/admin-center:1.0.0
```

## 查看构建的镜像

```bash
# 查看所有镜像
docker images

# 查看特定项目的镜像
docker images | grep workflow-platform

# 查看镜像详细信息
docker inspect workflow-platform/admin-center:latest
```

## 测试镜像

### 运行单个服务测试

```bash
# 运行后端服务（需要先启动数据库和 Redis）
docker run -d \
  --name test-admin-center \
  -p 8090:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/workflow_platform \
  -e SPRING_DATASOURCE_USERNAME=platform \
  -e SPRING_DATASOURCE_PASSWORD=platform123 \
  -e SPRING_REDIS_HOST=host.docker.internal \
  workflow-platform/admin-center:latest

# 查看日志
docker logs -f test-admin-center

# 停止并删除容器
docker stop test-admin-center
docker rm test-admin-center
```

## 优化建议

### 1. 使用 .dockerignore

确保每个服务目录都有 `.dockerignore` 文件，排除不必要的文件：

```
target/
.git/
.idea/
*.log
node_modules/
dist/
.env
```

### 2. 多阶段构建

后端 Dockerfile 应该使用多阶段构建：
- 第一阶段：使用 Maven 构建 JAR
- 第二阶段：使用 JRE 运行 JAR

### 3. 镜像大小优化

- 使用 Alpine Linux 基础镜像
- 只复制必要的文件
- 清理构建缓存

### 4. 构建缓存

使用 Docker BuildKit 加速构建：

```bash
export DOCKER_BUILDKIT=1
docker-compose build
```

## 常见问题

### 1. 构建失败：Maven 依赖下载超时

**解决方案**：配置 Maven 镜像或使用代理

```bash
# 在 Dockerfile 中配置 Maven 镜像
RUN mkdir -p /root/.m2 && \
    echo '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>*</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml
```

### 2. 前端构建失败：Node 版本不匹配

**解决方案**：确保 Dockerfile 中使用正确的 Node 版本

```dockerfile
FROM node:18-alpine AS builder
```

### 3. 镜像太大

**解决方案**：
- 使用多阶段构建
- 使用 Alpine 基础镜像
- 清理不必要的文件

### 4. 构建时间太长

**解决方案**：
- 使用构建缓存
- 并行构建多个服务
- 优化 Dockerfile 层顺序

## 完整构建示例

```bash
# 1. 构建所有镜像
docker-compose build

# 2. 查看构建的镜像
docker images | grep platform

# 3. 保存镜像到文件（可选）
docker save workflow-platform/admin-center:latest | gzip > admin-center.tar.gz

# 4. 加载镜像（在其他机器上）
docker load < admin-center.tar.gz

# 5. 推送到仓库
docker tag workflow-platform/admin-center:latest your-registry.com/workflow-platform/admin-center:1.0.0
docker push your-registry.com/workflow-platform/admin-center:1.0.0
```

## 生产环境部署

### 使用 Docker Compose 部署

```bash
# 使用生产环境配置
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 使用 Kubernetes

```bash
# 将镜像推送到 Kubernetes 可访问的仓库
docker push your-registry.com/workflow-platform/admin-center:1.0.0

# 使用 Helm 或 kubectl 部署
kubectl apply -f k8s/
```

## 相关文件

- `docker-compose.yml` - Docker Compose 配置文件
- `backend/*/Dockerfile` - 后端服务 Dockerfile
- `frontend/*/Dockerfile` - 前端服务 Dockerfile
