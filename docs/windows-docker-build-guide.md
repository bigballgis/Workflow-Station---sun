# Windows 系统 Docker 镜像打包完整指南

本文档提供在 Windows 系统上打包 Docker 镜像的完整步骤，适合初学者。

## 📋 目录

1. [环境准备](#环境准备)
2. [项目准备](#项目准备)
3. [构建镜像](#构建镜像)
4. [验证镜像](#验证镜像)
5. [常见问题](#常见问题)

---

## 第一步：环境准备

### 1.1 安装 Docker Desktop

1. **下载 Docker Desktop**
   - 访问：https://www.docker.com/products/docker-desktop/
   - 点击 "Download for Windows"
   - 下载 `Docker Desktop Installer.exe`

2. **安装 Docker Desktop**
   - 双击下载的安装程序
   - 按照向导完成安装
   - **重要**：安装过程中勾选 "Use WSL 2 instead of Hyper-V"（推荐）

3. **启动 Docker Desktop**
   - 安装完成后，从开始菜单启动 "Docker Desktop"
   - 等待 Docker 启动完成（系统托盘图标不再闪烁）
   - 首次启动可能需要几分钟

4. **验证 Docker 安装**
   - 打开 PowerShell 或命令提示符（CMD）
   - 输入以下命令：
     ```powershell
     docker --version
     ```
   - 应该显示类似：`Docker version 24.0.0, build xxxxx`
   - 再输入：
     ```powershell
     docker ps
     ```
   - 应该显示容器列表（可能为空，这是正常的）

### 1.2 安装 Git（如果还没有）

1. **检查是否已安装 Git**
   - 打开 PowerShell
   - 输入：`git --version`
   - 如果显示版本号，说明已安装，跳过此步骤

2. **安装 Git**
   - 访问：https://git-scm.com/download/win
   - 下载并安装 Git for Windows
   - 安装时使用默认选项即可

### 1.3 安装 Maven（用于构建后端服务）

1. **下载 Maven**
   - 访问：https://maven.apache.org/download.cgi
   - 下载 `apache-maven-3.9.x-bin.zip`（选择最新版本）

2. **解压 Maven**
   - 解压到 `C:\Program Files\Apache\maven`（或你喜欢的路径）
   - 记住这个路径，例如：`C:\Program Files\Apache\maven`

3. **配置环境变量**
   - 按 `Win + R`，输入 `sysdm.cpl`，回车
   - 点击 "高级" 标签
   - 点击 "环境变量"
   - 在 "系统变量" 区域，点击 "新建"
   - 变量名：`MAVEN_HOME`
   - 变量值：`C:\Program Files\Apache\maven`（你的 Maven 路径）
   - 点击 "确定"
   - 找到 "Path" 变量，点击 "编辑"
   - 点击 "新建"，输入：`%MAVEN_HOME%\bin`
   - 点击 "确定" 保存所有更改

4. **验证 Maven 安装**
   - **关闭并重新打开** PowerShell（重要！）
   - 输入：
     ```powershell
     mvn --version
     ```
   - 应该显示 Maven 版本信息

### 1.4 安装 Node.js（用于构建前端服务）

1. **下载 Node.js**
   - 访问：https://nodejs.org/
   - 下载 LTS 版本（推荐，例如 v20.x.x）
   - 下载 Windows Installer (.msi)

2. **安装 Node.js**
   - 双击安装程序
   - 按照向导完成安装（使用默认选项）
   - 安装程序会自动配置环境变量

3. **验证 Node.js 安装**
   - 打开新的 PowerShell
   - 输入：
     ```powershell
     node --version
     npm --version
     ```
   - 应该显示版本号

---

## 第二步：项目准备

### 2.1 打开项目目录

1. **打开 PowerShell**
   - 按 `Win + X`，选择 "Windows PowerShell" 或 "终端"
   - 或者按 `Win + R`，输入 `powershell`，回车

2. **导航到项目目录**
   ```powershell
   # 替换为你的实际项目路径
   cd "C:\Users\你的用户名\Desktop\PROJECTXXXSUN\Workflow-Station---sun"
   
   # 或者如果项目在其他位置
   cd "D:\Projects\Workflow-Station---sun"
   ```

3. **确认项目结构**
   ```powershell
   # 查看项目目录结构
   dir
   
   # 应该看到以下目录：
   # - backend/
   # - frontend/
   # - docker-compose.yml
   # - build-all-images.sh
   ```

### 2.2 构建后端 JAR 文件（重要！）

**⚠️ 注意：Docker 构建需要先有 JAR 文件，所以必须先构建后端服务**

1. **构建所有后端模块**
   ```powershell
   # 在项目根目录执行
   mvn clean package -DskipTests
   ```
   
   **说明：**
   - `clean`：清理之前的构建
   - `package`：打包成 JAR
   - `-DskipTests`：跳过测试（加快构建速度）
   - 这个过程可能需要 5-15 分钟，请耐心等待

2. **验证 JAR 文件已生成**
   ```powershell
   # 检查各个服务的 JAR 文件
   dir backend\admin-center\target\*.jar
   dir backend\workflow-engine-core\target\*.jar
   dir backend\user-portal\target\*.jar
   dir backend\developer-workstation\target\*.jar
   dir backend\api-gateway\target\*.jar
   ```
   
   应该看到类似 `admin-center-1.0.0-SNAPSHOT.jar` 的文件

### 2.3 构建前端项目（可选，Docker 会自动构建）

如果你想在本地先测试前端构建：

```powershell
# 构建 Admin Center 前端
cd frontend\admin-center
npm install
npm run build
cd ..\..

# 构建 Developer Workstation 前端
cd frontend\developer-workstation
npm install
npm run build
cd ..\..

# 构建 User Portal 前端
cd frontend\user-portal
npm install
npm run build
cd ..\..
```

**注意：** Docker 构建时会自动执行这些步骤，所以这一步是可选的。

---

## 第三步：构建镜像

### 方法 A：使用 Docker Compose（推荐，最简单）

1. **构建所有镜像**
   ```powershell
   # 在项目根目录执行
   docker-compose build
   ```
   
   **说明：**
   - 这会构建所有在 `docker-compose.yml` 中定义的服务
   - 包括 5 个后端服务和 3 个前端服务
   - 第一次构建可能需要 20-40 分钟（取决于网络速度）
   - 后续构建会使用缓存，会快很多

2. **查看构建进度**
   - Docker Desktop 会显示构建进度
   - 或者在 PowerShell 中可以看到构建日志

3. **构建完成后的提示**
   - 看到 "Successfully built" 和 "Successfully tagged" 表示成功
   - 如果看到错误，请参考 [常见问题](#常见问题) 部分

### 方法 B：使用构建脚本（需要 Git Bash 或 WSL）

如果你安装了 Git Bash 或 WSL：

1. **打开 Git Bash**
   - 在项目目录右键，选择 "Git Bash Here"
   - 或者从开始菜单打开 Git Bash，然后 `cd` 到项目目录

2. **执行构建脚本**
   ```bash
   # 赋予执行权限（仅第一次需要）
   chmod +x build-all-images.sh
   
   # 执行构建
   ./build-all-images.sh
   ```

### 方法 C：逐个构建服务（最灵活）

如果你想单独构建某个服务：

```powershell
# 构建 API Gateway
docker build -t workflow-platform/api-gateway:latest .\backend\api-gateway

# 构建 Workflow Engine
docker build -t workflow-platform/workflow-engine:latest .\backend\workflow-engine-core

# 构建 Admin Center
docker build -t workflow-platform/admin-center:latest .\backend\admin-center

# 构建 Developer Workstation
docker build -t workflow-platform/developer-workstation:latest .\backend\developer-workstation

# 构建 User Portal
docker build -t workflow-platform/user-portal:latest .\backend\user-portal

# 构建前端服务
docker build -t workflow-platform/frontend-admin:latest .\frontend\admin-center
docker build -t workflow-platform/frontend-developer:latest .\frontend\developer-workstation
docker build -t workflow-platform/frontend-portal:latest .\frontend\user-portal
```

---

## 第四步：验证镜像

### 4.1 查看所有构建的镜像

```powershell
# 查看所有 workflow-platform 相关的镜像
docker images | Select-String workflow-platform

# 或者查看所有镜像
docker images
```

**应该看到类似以下输出：**
```
REPOSITORY                          TAG       IMAGE ID       CREATED         SIZE
workflow-platform/admin-center      latest    xxxxx          5 minutes ago   250MB
workflow-platform/api-gateway       latest    xxxxx          5 minutes ago   245MB
workflow-platform/workflow-engine   latest    xxxxx          5 minutes ago   280MB
...
```

### 4.2 验证镜像数量

应该看到 **8 个镜像**：
- 5 个后端服务镜像
- 3 个前端服务镜像

### 4.3 测试运行镜像（可选）

```powershell
# 测试运行 Admin Center（需要先启动数据库）
docker run -d --name test-admin-center -p 8090:8080 workflow-platform/admin-center:latest

# 查看日志
docker logs test-admin-center

# 停止并删除测试容器
docker stop test-admin-center
docker rm test-admin-center
```

---

## 第五步：保存镜像（可选）

如果你想将镜像保存到文件，以便在其他机器上使用：

```powershell
# 创建保存目录
mkdir docker-images

# 保存所有镜像（逐个保存）
docker save workflow-platform/admin-center:latest -o docker-images\admin-center.tar
docker save workflow-platform/api-gateway:latest -o docker-images\api-gateway.tar
docker save workflow-platform/workflow-engine:latest -o docker-images\workflow-engine.tar
docker save workflow-platform/user-portal:latest -o docker-images\user-portal.tar
docker save workflow-platform/developer-workstation:latest -o docker-images\developer-workstation.tar
docker save workflow-platform/frontend-admin:latest -o docker-images\frontend-admin.tar
docker save workflow-platform/frontend-developer:latest -o docker-images\frontend-developer.tar
docker save workflow-platform/frontend-portal:latest -o docker-images\frontend-portal.tar
```

**在其他机器上加载镜像：**
```powershell
docker load -i docker-images\admin-center.tar
```

---

## 常见问题

### 问题 1：Docker 命令找不到

**错误信息：** `'docker' 不是内部或外部命令`

**解决方法：**
1. 确保 Docker Desktop 已启动
2. 重启 PowerShell
3. 如果还是不行，检查 Docker Desktop 是否正常安装

### 问题 2：Maven 构建失败

**错误信息：** `'mvn' 不是内部或外部命令` 或构建超时

**解决方法：**
1. 确认 Maven 已正确安装并配置环境变量
2. **关闭并重新打开** PowerShell
3. 检查网络连接（Maven 需要下载依赖）
4. 如果网络慢，可以配置 Maven 镜像：
   - 编辑 `C:\Users\你的用户名\.m2\settings.xml`（如果不存在则创建）
   - 添加阿里云镜像配置（参考 Maven 镜像配置）

### 问题 3：Docker 构建失败 - "找不到 JAR 文件"

**错误信息：** `COPY failed: file not found in build context`

**解决方法：**
1. 确保先执行了 `mvn clean package` 构建 JAR 文件
2. 检查 JAR 文件是否在 `target` 目录下：
   ```powershell
   dir backend\admin-center\target\*.jar
   ```
3. 如果 JAR 文件不存在，重新执行 Maven 构建

### 问题 4：Docker 构建失败 - "npm install 失败"

**错误信息：** `npm ERR!` 相关错误

**解决方法：**
1. 检查 Node.js 是否正确安装
2. 检查网络连接
3. 如果网络慢，可以配置 npm 镜像：
   ```powershell
   npm config set registry https://registry.npmmirror.com
   ```

### 问题 5：Docker Desktop 启动失败

**错误信息：** Docker Desktop 无法启动

**解决方法：**
1. 确保已启用虚拟化（在 BIOS 中）
2. 确保已启用 WSL 2 或 Hyper-V
3. 重启电脑
4. 如果还是不行，尝试重新安装 Docker Desktop

### 问题 6：磁盘空间不足

**错误信息：** `no space left on device`

**解决方法：**
1. 清理 Docker 未使用的资源：
   ```powershell
   docker system prune -a
   ```
2. 删除未使用的镜像：
   ```powershell
   docker image prune -a
   ```

### 问题 7：构建速度很慢

**解决方法：**
1. 使用 Docker BuildKit（自动启用）：
   ```powershell
   $env:DOCKER_BUILDKIT=1
   docker-compose build
   ```
2. 使用国内镜像源（配置 Docker Desktop 镜像加速）
3. 确保网络连接稳定

---

## 快速检查清单

在开始构建前，请确认：

- [ ] Docker Desktop 已安装并运行
- [ ] Maven 已安装并配置环境变量
- [ ] Node.js 已安装
- [ ] 项目目录正确
- [ ] 已执行 `mvn clean package` 构建 JAR 文件
- [ ] 网络连接正常

---

## 完整操作流程总结

1. **安装环境**
   - 安装 Docker Desktop
   - 安装 Maven
   - 安装 Node.js

2. **准备项目**
   - 打开项目目录
   - 执行 `mvn clean package -DskipTests` 构建 JAR

3. **构建镜像**
   - 执行 `docker-compose build`

4. **验证结果**
   - 执行 `docker images` 查看镜像

5. **完成！**

---

## 需要帮助？

如果遇到问题：
1. 查看错误信息
2. 参考 [常见问题](#常见问题) 部分
3. 检查 Docker Desktop 日志
4. 查看项目文档

祝构建顺利！🎉
