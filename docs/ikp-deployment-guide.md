# IKP (Kubernetes) 部署镜像构建指南

本文档介绍如何为 IKP（Kubernetes 平台）部署构建和推送 Docker 镜像的最佳实践。

## 📋 目录

1. [推荐方案](#推荐方案)
2. [方案一：CI/CD 自动构建（推荐）](#方案一cicd-自动构建推荐)
3. [方案二：本地构建 + 手动推送](#方案二本地构建--手动推送)
4. [方案三：使用镜像仓库构建](#方案三使用镜像仓库构建)
5. [镜像优化建议](#镜像优化建议)
6. [部署到 IKP](#部署到-ikp)

---

## 推荐方案

### 🏆 最佳实践：CI/CD 自动构建 + 镜像仓库

**推荐理由：**
- ✅ 自动化，减少人工错误
- ✅ 版本管理清晰（Git Tag 对应镜像版本）
- ✅ 构建环境一致
- ✅ 支持多环境（开发/测试/生产）
- ✅ 集成代码审查流程

### 方案对比

| 方案 | 适用场景 | 优点 | 缺点 |
|------|---------|------|------|
| **CI/CD 自动构建** | 生产环境、团队协作 | 自动化、可追溯、标准化 | 需要配置 CI/CD |
| **本地构建 + 推送** | 开发测试、快速迭代 | 简单直接、快速 | 依赖本地环境 |
| **镜像仓库构建** | 无本地构建环境 | 无需本地环境 | 构建速度可能较慢 |

---

## 方案一：CI/CD 自动构建（推荐）

### 1.1 GitHub Actions 配置

创建 `.github/workflows/build-and-push.yml`：

```yaml
name: Build and Push Docker Images

on:
  push:
    branches:
      - main
      - develop
    tags:
      - 'v*'
  pull_request:
    branches:
      - main

env:
  REGISTRY: your-registry.com/workflow-platform  # 替换为你的镜像仓库地址
  IMAGE_TAG: ${{ github.ref == 'refs/heads/main' && 'latest' || github.sha }}

jobs:
  build-backend:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - api-gateway
          - workflow-engine-core
          - admin-center
          - developer-workstation
          - user-portal
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Build JAR
        working-directory: ./backend/${{ matrix.service }}
        run: mvn clean package -DskipTests

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to Container Registry
        uses: docker/login-action@v3
        with:
          registry: your-registry.com
          username: ${{ secrets.REGISTRY_USERNAME }}
          password: ${{ secrets.REGISTRY_PASSWORD }}

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./backend/${{ matrix.service }}
          push: ${{ github.event_name != 'pull_request' }}
          tags: |
            ${{ env.REGISTRY }}/${{ matrix.service }}:${{ env.IMAGE_TAG }}
            ${{ env.REGISTRY }}/${{ matrix.service }}:latest
          cache-from: type=registry,ref=${{ env.REGISTRY }}/${{ matrix.service }}:buildcache
          cache-to: type=registry,ref=${{ env.REGISTRY }}/${{ matrix.service }}:buildcache,mode=max

  build-frontend:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - admin-center
          - developer-workstation
          - user-portal
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/${{ matrix.service }}/package-lock.json

      - name: Install dependencies
        working-directory: ./frontend/${{ matrix.service }}
        run: npm ci

      - name: Build frontend
        working-directory: ./frontend/${{ matrix.service }}
        run: npm run build

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to Container Registry
        uses: docker/login-action@v3
        with:
          registry: your-registry.com
          username: ${{ secrets.REGISTRY_USERNAME }}
          password: ${{ secrets.REGISTRY_PASSWORD }}

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./frontend/${{ matrix.service }}
          push: ${{ github.event_name != 'pull_request' }}
          tags: |
            ${{ env.REGISTRY }}/frontend-${{ matrix.service }}:${{ env.IMAGE_TAG }}
            ${{ env.REGISTRY }}/frontend-${{ matrix.service }}:latest
          cache-from: type=registry,ref=${{ env.REGISTRY }}/frontend-${{ matrix.service }}:buildcache
          cache-to: type=registry,ref=${{ env.REGISTRY }}/frontend-${{ matrix.service }}:buildcache,mode=max
```

### 1.2 GitLab CI 配置

创建 `.gitlab-ci.yml`：

```yaml
stages:
  - build
  - push

variables:
  REGISTRY: your-registry.com/workflow-platform
  DOCKER_DRIVER: overlay2
  DOCKER_TLS_CERTDIR: "/certs"

before_script:
  - docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD $REGISTRY

build-backend:
  stage: build
  image: maven:3.9-eclipse-temurin-17
  script:
    - |
      for service in api-gateway workflow-engine-core admin-center developer-workstation user-portal; do
        echo "Building $service..."
        cd backend/$service
        mvn clean package -DskipTests
        docker build -t $REGISTRY/$service:$CI_COMMIT_SHORT_SHA -t $REGISTRY/$service:latest .
        docker push $REGISTRY/$service:$CI_COMMIT_SHORT_SHA
        docker push $REGISTRY/$service:latest
        cd ../..
      done
  only:
    - main
    - develop
    - tags

build-frontend:
  stage: build
  image: node:20-alpine
  script:
    - |
      for service in admin-center developer-workstation user-portal; do
        echo "Building frontend-$service..."
        cd frontend/$service
        npm ci
        npm run build
        docker build -t $REGISTRY/frontend-$service:$CI_COMMIT_SHORT_SHA -t $REGISTRY/frontend-$service:latest .
        docker push $REGISTRY/frontend-$service:$CI_COMMIT_SHORT_SHA
        docker push $REGISTRY/frontend-$service:latest
        cd ../..
      done
  only:
    - main
    - develop
    - tags
```

### 1.3 Jenkins Pipeline 配置

创建 `Jenkinsfile`：

```groovy
pipeline {
    agent any
    
    environment {
        REGISTRY = 'your-registry.com/workflow-platform'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }
    
    stages {
        stage('Build Backend') {
            steps {
                script {
                    def services = ['api-gateway', 'workflow-engine-core', 'admin-center', 'developer-workstation', 'user-portal']
                    services.each { service ->
                        sh """
                            cd backend/${service}
                            mvn clean package -DskipTests
                            docker build -t ${REGISTRY}/${service}:${IMAGE_TAG} -t ${REGISTRY}/${service}:latest .
                            docker push ${REGISTRY}/${service}:${IMAGE_TAG}
                            docker push ${REGISTRY}/${service}:latest
                        """
                    }
                }
            }
        }
        
        stage('Build Frontend') {
            steps {
                script {
                    def services = ['admin-center', 'developer-workstation', 'user-portal']
                    services.each { service ->
                        sh """
                            cd frontend/${service}
                            npm ci
                            npm run build
                            docker build -t ${REGISTRY}/frontend-${service}:${IMAGE_TAG} -t ${REGISTRY}/frontend-${service}:latest .
                            docker push ${REGISTRY}/frontend-${service}:${IMAGE_TAG}
                            docker push ${REGISTRY}/frontend-${service}:latest
                        """
                    }
                }
            }
        }
    }
}
```

---

## 方案二：本地构建 + 手动推送

### 2.1 构建脚本（Windows PowerShell）

创建 `build-and-push.ps1`：

```powershell
param(
    [string]$Version = "latest",
    [string]$Registry = "your-registry.com/workflow-platform",
    [string]$Username = "",
    [string]$Password = ""
)

# 登录镜像仓库
if ($Username -and $Password) {
    $securePassword = ConvertTo-SecureString $Password -AsPlainText -Force
    $credential = New-Object System.Management.Automation.PSCredential($Username, $securePassword)
    docker login $Registry -u $Username -p $Password
}

# 构建后端服务
$backendServices = @("api-gateway", "workflow-engine-core", "admin-center", "developer-workstation", "user-portal")
foreach ($service in $backendServices) {
    Write-Host "Building $service..." -ForegroundColor Yellow
    docker build -t "$Registry/$service`:$Version" -t "$Registry/$service`:latest" ".\backend\$service"
    docker push "$Registry/$service`:$Version"
    docker push "$Registry/$service`:latest"
}

# 构建前端服务
$frontendServices = @("admin-center", "developer-workstation", "user-portal")
foreach ($service in $frontendServices) {
    Write-Host "Building frontend-$service..." -ForegroundColor Yellow
    docker build -t "$Registry/frontend-$service`:$Version" -t "$Registry/frontend-$service`:latest" ".\frontend\$service"
    docker push "$Registry/frontend-$service`:$Version"
    docker push "$Registry/frontend-$service`:latest"
}

Write-Host "All images built and pushed successfully!" -ForegroundColor Green
```

### 2.2 使用方法

```powershell
# 基本用法
.\build-and-push.ps1 -Version "1.0.0" -Registry "your-registry.com/workflow-platform"

# 带认证
.\build-and-push.ps1 -Version "1.0.0" -Registry "your-registry.com/workflow-platform" -Username "your-username" -Password "your-password"
```

---

## 方案三：使用镜像仓库构建

### 3.1 阿里云 ACR（容器镜像服务）

1. **在 ACR 控制台创建构建规则**
   - 登录阿里云 ACR 控制台
   - 创建命名空间：`workflow-platform`
   - 为每个服务创建构建规则
   - 配置代码源（GitHub/GitLab）
   - 配置构建命令

2. **构建配置示例**
   ```bash
   # 后端服务构建命令
   cd backend/admin-center
   mvn clean package -DskipTests
   docker build -t $IMAGE_TAG .
   
   # 前端服务构建命令
   cd frontend/admin-center
   npm ci
   npm run build
   docker build -t $IMAGE_TAG .
   ```

### 3.2 腾讯云 TCR

类似阿里云 ACR，在 TCR 控制台配置自动构建规则。

---

## 镜像优化建议

### 1. 多阶段构建优化

确保 Dockerfile 使用多阶段构建：

```dockerfile
# 后端服务示例
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2. 使用 .dockerignore

每个服务目录创建 `.dockerignore`：

```
target/
.git/
.idea/
*.log
node_modules/
dist/
.env
```

### 3. 构建缓存优化

使用 BuildKit 和缓存：

```bash
export DOCKER_BUILDKIT=1
docker build --cache-from type=registry,ref=your-registry.com/service:buildcache \
             --cache-to type=registry,ref=your-registry.com/service:buildcache,mode=max \
             -t your-registry.com/service:latest .
```

---

## 部署到 IKP

### 1. 准备 Kubernetes 配置文件

创建 `k8s/deployment.yaml`：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: admin-center
  namespace: workflow-platform
spec:
  replicas: 2
  selector:
    matchLabels:
      app: admin-center
  template:
    metadata:
      labels:
        app: admin-center
    spec:
      containers:
      - name: admin-center
        image: your-registry.com/workflow-platform/admin-center:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: username
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: password
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /api/v1/admin/actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /api/v1/admin/actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: admin-center
  namespace: workflow-platform
spec:
  selector:
    app: admin-center
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
```

### 2. 配置镜像拉取密钥

```bash
# 创建 Secret（如果使用私有仓库）
kubectl create secret docker-registry regcred \
  --docker-server=your-registry.com \
  --docker-username=your-username \
  --docker-password=your-password \
  --namespace=workflow-platform
```

### 3. 部署到 IKP

```bash
# 应用配置
kubectl apply -f k8s/

# 查看部署状态
kubectl get pods -n workflow-platform

# 查看服务
kubectl get svc -n workflow-platform
```

---

## 推荐工作流程

### 开发环境
1. 本地构建测试
2. 推送到开发环境镜像仓库
3. IKP 自动拉取部署

### 生产环境
1. **代码合并到 main 分支**
2. **CI/CD 自动触发构建**
3. **自动推送到生产镜像仓库**
4. **自动或手动部署到 IKP**

---

## 最佳实践总结

1. ✅ **使用 CI/CD 自动构建**（GitHub Actions/GitLab CI）
2. ✅ **使用镜像仓库**（ACR/TCR/Docker Hub）
3. ✅ **版本标签管理**（Git Tag 对应镜像版本）
4. ✅ **多阶段构建优化镜像大小**
5. ✅ **使用构建缓存加速构建**
6. ✅ **配置健康检查**
7. ✅ **使用 Secret 管理敏感信息**

---

## 快速开始

### 最简单的方式（推荐）

1. **配置 GitHub Actions**（如果使用 GitHub）
   - 复制上面的 GitHub Actions 配置
   - 配置 Secrets：`REGISTRY_USERNAME` 和 `REGISTRY_PASSWORD`
   - 推送代码，自动构建

2. **或者使用本地构建脚本**
   ```powershell
   .\build-and-push.ps1 -Version "1.0.0" -Registry "your-registry.com/workflow-platform"
   ```

3. **部署到 IKP**
   ```bash
   kubectl apply -f k8s/
   ```

---

需要我帮你创建具体的 CI/CD 配置文件吗？
