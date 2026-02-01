# Docker 镜像拉取问题解决方案

## 问题描述

构建 Docker 镜像时出现错误：
```
load metadata for docker.io/library/node:20-alpine ERROR
```

这通常是因为无法访问 Docker Hub（网络问题或防火墙限制）。

## 解决方案

### 方案一：配置 Docker 镜像加速器（推荐）

#### Windows Docker Desktop

1. **打开 Docker Desktop**
2. **点击设置图标（齿轮）**
3. **选择 "Docker Engine"**
4. **在 JSON 配置中添加镜像加速器**：

```json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
```

5. **点击 "Apply & Restart"**
6. **等待 Docker 重启完成**

#### 验证配置

```powershell
# 查看 Docker 配置
docker info | Select-String -Pattern "Registry Mirrors"
```

应该看到配置的镜像地址。

### 方案二：修改 Dockerfile 使用国内镜像源

#### 修改前端 Dockerfile

将 `FROM node:20-alpine` 改为使用国内镜像：

**选项 1：使用阿里云镜像**
```dockerfile
FROM registry.cn-hangzhou.aliyuncs.com/acs/node:20-alpine AS builder
```

**选项 2：使用腾讯云镜像**
```dockerfile
FROM ccr.ccs.tencentyun.com/library/node:20-alpine AS builder
```

**选项 3：使用网易镜像**
```dockerfile
FROM hub.c.163.com/library/node:20-alpine AS builder
```

#### 修改后端 Dockerfile

如果后端也有类似问题，将 `FROM eclipse-temurin:17-jre-alpine` 改为：

```dockerfile
FROM registry.cn-hangzhou.aliyuncs.com/google_containers/eclipse-temurin:17-jre-alpine
```

### 方案三：使用代理（如果公司有代理）

如果公司有代理服务器：

1. **在 Docker Desktop 设置中配置代理**
   - 打开 Docker Desktop 设置
   - 选择 "Resources" -> "Proxies"
   - 配置 HTTP/HTTPS 代理

2. **或者在 Dockerfile 中配置代理**
```dockerfile
FROM node:20-alpine AS builder

# 设置代理（如果需要）
ARG HTTP_PROXY
ARG HTTPS_PROXY
ENV HTTP_PROXY=${HTTP_PROXY}
ENV HTTPS_PROXY=${HTTPS_PROXY}

WORKDIR /app
# ... 其他内容
```

## 快速修复步骤（Windows）

### 步骤 1：配置镜像加速器

1. 右键点击系统托盘中的 Docker 图标
2. 选择 "Settings"（设置）
3. 点击左侧 "Docker Engine"
4. 在右侧 JSON 编辑器中，添加以下内容：

```json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
```

5. 点击 "Apply & Restart"

### 步骤 2：验证配置

打开 PowerShell，执行：

```powershell
docker info
```

查找 "Registry Mirrors" 部分，应该看到配置的镜像地址。

### 步骤 3：重新构建

```powershell
# 清理构建缓存
docker builder prune

# 重新构建
docker build -t workflow-platform/frontend-admin:latest .\frontend\admin-center
```

## 常用国内镜像加速器

| 镜像加速器 | 地址 | 说明 |
|-----------|------|------|
| 中科大镜像 | https://docker.mirrors.ustc.edu.cn | 推荐，速度快 |
| 网易镜像 | https://hub-mirror.c.163.com | 稳定 |
| 百度云镜像 | https://mirror.baidubce.com | 百度提供 |
| 阿里云镜像 | 需要登录阿里云获取 | 个人专属地址 |

### 获取阿里云专属镜像地址

1. 登录阿里云：https://cr.console.aliyun.com/
2. 进入 "容器镜像服务" -> "镜像加速器"
3. 复制专属加速地址
4. 添加到 Docker 配置中

## 如果还是不行

### 方案 A：手动拉取镜像

```powershell
# 先手动拉取基础镜像
docker pull node:20-alpine

# 如果还是失败，使用国内镜像
docker pull registry.cn-hangzhou.aliyuncs.com/acs/node:20-alpine
docker tag registry.cn-hangzhou.aliyuncs.com/acs/node:20-alpine node:20-alpine
```

### 方案 B：修改 Dockerfile 使用国内镜像源

创建一个修复脚本，批量替换所有 Dockerfile：

```powershell
# 替换所有前端 Dockerfile 中的 node 镜像
Get-ChildItem -Path "frontend\*\Dockerfile" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $content = $content -replace 'FROM node:20-alpine', 'FROM registry.cn-hangzhou.aliyuncs.com/acs/node:20-alpine'
    Set-Content -Path $_.FullName -Value $content
    Write-Host "已更新: $($_.FullName)"
}
```

## 验证修复

### 方法一：使用验证脚本（推荐）

运行验证脚本：

```powershell
.\verify-docker-mirror.ps1
```

脚本会自动：
- ✅ 检查 Docker 配置
- ✅ 查看镜像加速器配置
- ✅ 测试拉取镜像速度
- ✅ 验证构建功能

### 方法二：手动验证

#### 1. 查看 Docker 配置

```powershell
# 查看 Docker 信息，查找镜像加速器配置
docker info | Select-String -Pattern "Registry Mirrors"
```

**应该看到类似输出：**
```
Registry Mirrors:
 https://docker.mirrors.ustc.edu.cn/
 https://hub-mirror.c.163.com/
 https://mirror.baidubce.com/
```

#### 2. 测试拉取镜像

```powershell
# 测试拉取镜像（观察下载速度）
docker pull node:20-alpine
```

**判断标准：**
- ✅ **速度快（< 30秒）**：镜像加速器已生效
- ⚠️ **速度一般（30-120秒）**：可能部分生效或网络问题
- ❌ **速度很慢（> 2分钟）**：镜像加速器可能未生效

#### 3. 查看镜像详细信息

```powershell
# 查看镜像信息
docker images node:20-alpine

# 查看镜像详细信息
docker inspect node:20-alpine
```

#### 4. 实际构建测试

```powershell
# 测试构建前端镜像
docker build -t test-build .\frontend\admin-center

# 如果构建成功且速度快，说明加速器生效
```

### 方法三：对比测试

#### 测试 1：不使用加速器（如果可能）

```powershell
# 临时禁用加速器，测试原始速度
# 在 Docker Desktop 中临时移除镜像加速器配置
docker pull node:20-alpine
# 记录耗时
```

#### 测试 2：使用加速器

```powershell
# 启用加速器后，再次测试
docker pull node:20-alpine
# 对比耗时，如果明显加快，说明加速器生效
```

### 验证结果判断

| 情况 | 说明 | 处理 |
|------|------|------|
| ✅ 配置存在且拉取快 | 加速器已生效 | 可以正常使用 |
| ⚠️ 配置存在但拉取慢 | 加速器可能未生效 | 检查网络或更换加速器 |
| ❌ 配置不存在 | 未配置加速器 | 重新配置 |
| ❌ 拉取失败 | 网络问题 | 使用国内镜像源 |

### 快速验证命令

```powershell
# 一键验证（查看配置）
docker info | Select-String -Pattern "Registry Mirrors" -Context 0,3

# 快速测试（拉取小镜像）
docker pull alpine:latest

# 查看拉取速度
Measure-Command { docker pull alpine:latest }
```

## 常见错误

### 错误 1：TLS handshake timeout

**原因**：网络超时

**解决**：
1. 配置镜像加速器
2. 使用国内镜像源
3. 检查网络连接

### 错误 2：unauthorized: authentication required

**原因**：需要登录

**解决**：
```powershell
docker login
```

### 错误 3：pull access denied

**原因**：镜像不存在或权限不足

**解决**：检查镜像名称是否正确

## 推荐配置（完整）

### Docker Desktop 配置（推荐）

```json
{
  "builder": {
    "gc": {
      "defaultKeepStorage": "20GB",
      "enabled": true
    }
  },
  "experimental": false,
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
```

### 如果使用阿里云

```json
{
  "registry-mirrors": [
    "https://你的专属地址.mirror.aliyuncs.com"
  ]
}
```

## 测试步骤

1. **配置镜像加速器**（按上面的步骤）
2. **重启 Docker Desktop**
3. **测试拉取镜像**：
   ```powershell
   docker pull node:20-alpine
   ```
4. **如果成功，重新构建**：
   ```powershell
   docker build -t test-image .\frontend\admin-center
   ```

## 总结

**最快解决方案**：
1. 配置 Docker 镜像加速器（方案一）
2. 重启 Docker Desktop
3. 重新构建镜像

如果还是不行，使用方案二：修改 Dockerfile 使用国内镜像源。
