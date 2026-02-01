# Apple Silicon (ARM64) 兼容性修复

## 问题描述

在 Apple Silicon (M1/M2/M3) MacBook 上运行 Docker 构建时遇到以下错误：
```
=> ERROR [internal] load metadata for docker.io/library/eclipse-temurin:1
```

## 根本原因

1. **平台不兼容**: 原始 Dockerfile 使用 `--platform=linux/amd64` 强制使用 x86_64 架构
2. **镜像不可用**: `eclipse-temurin:17-jre-alpine` 镜像在 ARM64 平台上不可用
3. **Alpine 工具差异**: Alpine Linux 使用不同的包管理器和用户管理命令

## 修复方案

### 1. 移除平台限制

**之前 (不兼容):**
```dockerfile
FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine
```

**修复后 (兼容):**
```dockerfile
FROM eclipse-temurin:17-jre
```

### 2. 更换基础镜像

- **之前**: `eclipse-temurin:17-jre-alpine` (Alpine Linux)
- **修复后**: `eclipse-temurin:17-jre` (Ubuntu-based)

### 3. 更新用户管理命令

**Alpine Linux (之前):**
```dockerfile
RUN addgroup -S platform && adduser -S platform -G platform
```

**Ubuntu (修复后):**
```dockerfile
RUN groupadd -r platform && useradd -r -g platform platform
```

### 4. 更新健康检查命令

**Alpine Linux (之前):**
```dockerfile
CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
```

**Ubuntu (修复后):**
```dockerfile
CMD curl -f http://localhost:8080/actuator/health || exit 1
```

### 5. 更新 Shell 命令

**Alpine Linux (之前):**
```dockerfile
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Ubuntu (修复后):**
```dockerfile
ENTRYPOINT ["bash", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 6. 移除构建脚本中的平台限制

**Shell 脚本 (start-all.sh):**
```bash
# 之前
docker build --platform linux/amd64 -f "$dockerfile" -t "$image_name" "$context"

# 修复后
docker build -f "$dockerfile" -t "$image_name" "$context"
```

**PowerShell 脚本 (start-all.ps1):**
```powershell
# 之前
docker build --platform linux/amd64 -f $Dockerfile -t $ImageName $Context

# 修复后
docker build -f $Dockerfile -t $ImageName $Context
```

## 修复的文件

### Dockerfile 文件
- [x] `backend/workflow-engine-core/Dockerfile`
- [x] `backend/admin-center/Dockerfile`
- [x] `backend/user-portal/Dockerfile`
- [x] `backend/developer-workstation/Dockerfile`
- [x] `backend/api-gateway/Dockerfile`

### 启动脚本
- [x] `start-all.sh` - macOS/Linux 兼容脚本
- [x] `start-all.ps1` - PowerShell 脚本

## 验证步骤

### 1. 清理 Docker 缓存
```bash
docker system prune -f
```

### 2. 拉取基础镜像
```bash
docker pull eclipse-temurin:17-jre
```

### 3. 测试构建单个服务
```bash
cd backend/workflow-engine-core
docker build -t test-workflow-engine .
```

### 4. 运行完整启动脚本
```bash
./start-all.sh --infra-only  # 先测试基础设施
./start-all.sh --backend-only # 再测试后端服务
```

## 性能影响

### 镜像大小对比
- **Alpine**: ~200MB (更小)
- **Ubuntu**: ~400MB (更大，但兼容性更好)

### 启动时间
- **Alpine**: 稍快
- **Ubuntu**: 稍慢，但差异不明显

### 内存使用
- 两者内存使用基本相同

## 兼容性矩阵

| 平台 | Alpine 版本 | Ubuntu 版本 | 状态 |
|------|-------------|-------------|------|
| Intel Mac | ✅ | ✅ | 都支持 |
| Apple Silicon | ❌ | ✅ | 只支持 Ubuntu |
| Linux x86_64 | ✅ | ✅ | 都支持 |
| Linux ARM64 | ❌ | ✅ | 只支持 Ubuntu |

## 建议

1. **开发环境**: 使用 Ubuntu 基础镜像确保跨平台兼容性
2. **生产环境**: 可以考虑使用 Alpine 镜像减少镜像大小（如果部署在 x86_64 平台）
3. **CI/CD**: 配置多架构构建支持不同平台

## 未来改进

1. **多架构构建**: 使用 `docker buildx` 构建支持多架构的镜像
2. **镜像优化**: 使用多阶段构建减少最终镜像大小
3. **健康检查优化**: 使用更轻量的健康检查方式

## 相关文档

- [Docker Multi-platform builds](https://docs.docker.com/build/building/multi-platform/)
- [Eclipse Temurin Docker Images](https://hub.docker.com/_/eclipse-temurin)
- [Apple Silicon Docker 最佳实践](https://docs.docker.com/desktop/mac/apple-silicon/)