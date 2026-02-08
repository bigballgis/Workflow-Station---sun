# Docker 容器 JAR 文件更新指南

## 问题说明

在开发环境中，当我们修改代码并重新编译后，Docker 容器中的 JAR 文件不会自动更新。这是因为：

1. Docker 容器在构建时会将 JAR 文件复制到容器内部
2. 重启容器只是重新启动应用，不会重新复制 JAR 文件
3. 需要手动更新容器中的 JAR 文件或重新构建镜像

## 解决方案

### 方案一：手动复制 JAR 文件（快速）

适用于开发环境快速测试。

#### 步骤

1. **编译项目**
   ```bash
   mvn clean install -DskipTests -pl backend/admin-center -am
   ```

2. **复制 JAR 文件到容器**
   ```bash
   docker cp backend/admin-center/target/admin-center-1.0.0.jar platform-admin-center-dev:/app/app.jar
   ```

3. **重启容器**
   ```bash
   docker restart platform-admin-center-dev
   ```

4. **验证更新**
   ```bash
   docker exec platform-admin-center-dev ls -lh /app/app.jar
   ```
   检查文件时间戳是否为最新。

#### 其他模块

- **developer-workstation**:
  ```bash
  mvn clean install -DskipTests -pl backend/developer-workstation -am
  docker cp backend/developer-workstation/target/developer-workstation-1.0.0.jar platform-developer-workstation-dev:/app/app.jar
  docker restart platform-developer-workstation-dev
  ```

- **user-portal**:
  ```bash
  mvn clean install -DskipTests -pl backend/user-portal -am
  docker cp backend/user-portal/target/user-portal-1.0.0.jar platform-user-portal-dev:/app/app.jar
  docker restart platform-user-portal-dev
  ```

- **workflow-engine-core**:
  ```bash
  mvn clean install -DskipTests -pl backend/workflow-engine-core -am
  docker cp backend/workflow-engine-core/target/workflow-engine-core-1.0.0.jar platform-workflow-engine-dev:/app/app.jar
  docker restart platform-workflow-engine-dev
  ```

### 方案二：重新构建 Docker 镜像（推荐用于生产）

适用于需要完整重新构建的场景。

#### 步骤

1. **编译项目**
   ```bash
   mvn clean install -DskipTests
   ```

2. **重新构建 Docker 镜像**
   ```bash
   docker-compose build admin-center
   ```

3. **重启服务**
   ```bash
   docker-compose up -d admin-center
   ```

## 常见问题

### Q: 为什么重启容器后还是旧代码？

A: 因为容器中的 JAR 文件没有更新。需要使用上述方案之一更新 JAR 文件。

### Q: 如何确认 JAR 文件已更新？

A: 使用以下命令检查文件时间戳：
```bash
docker exec platform-admin-center-dev ls -lh /app/app.jar
```

对比本地编译的 JAR 文件时间：
```bash
# Windows PowerShell
Get-Item "backend\admin-center\target\admin-center-1.0.0.jar" | Select-Object Name, Length, LastWriteTime

# Linux/Mac
ls -lh backend/admin-center/target/admin-center-1.0.0.jar
```

### Q: 能否自动化这个过程？

A: 可以创建一个脚本来自动化这个过程。例如：

**PowerShell 脚本** (`update-and-restart.ps1`):
```powershell
param(
    [Parameter(Mandatory=$true)]
    [string]$Module
)

$moduleMap = @{
    "admin-center" = @{
        "path" = "backend/admin-center"
        "jar" = "admin-center-1.0.0.jar"
        "container" = "platform-admin-center-dev"
    }
    "developer-workstation" = @{
        "path" = "backend/developer-workstation"
        "jar" = "developer-workstation-1.0.0.jar"
        "container" = "platform-developer-workstation-dev"
    }
}

$config = $moduleMap[$Module]
if (-not $config) {
    Write-Error "Unknown module: $Module"
    exit 1
}

Write-Host "Compiling $Module..." -ForegroundColor Cyan
mvn clean install -DskipTests -pl $config.path -am

Write-Host "Copying JAR to container..." -ForegroundColor Cyan
docker cp "$($config.path)/target/$($config.jar)" "$($config.container):/app/app.jar"

Write-Host "Restarting container..." -ForegroundColor Cyan
docker restart $config.container

Write-Host "Waiting for service to start..." -ForegroundColor Cyan
Start-Sleep -Seconds 30

Write-Host "Checking logs..." -ForegroundColor Cyan
docker logs $config.container --tail 20

Write-Host "Done!" -ForegroundColor Green
```

使用方法：
```powershell
.\update-and-restart.ps1 -Module admin-center
```

## 最佳实践

### 开发环境
- 使用方案一（手动复制）进行快速迭代
- 每次代码修改后记得更新容器中的 JAR 文件

### 测试环境
- 使用方案二（重新构建镜像）确保完整性
- 使用 docker-compose 管理服务

### 生产环境
- 始终使用方案二（重新构建镜像）
- 使用 CI/CD 流程自动化构建和部署
- 使用版本标签管理镜像

## 相关命令

### 查看容器中的文件
```bash
docker exec <container-name> ls -lh /app/
```

### 查看容器日志
```bash
docker logs <container-name> --tail 50
```

### 进入容器
```bash
docker exec -it <container-name> /bin/bash
```

### 检查容器状态
```bash
docker ps --filter "name=<container-name>"
```

---

**创建日期**: 2026-02-06  
**适用环境**: 开发环境  
**相关问题**: deployed_at 字段修复
