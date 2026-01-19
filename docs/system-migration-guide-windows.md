# Windows 系统迁移指南

生成时间: 2026-01-18

本指南将帮助您将整个工作流平台系统从当前环境迁移到另一台 Windows 电脑。

---

## 📋 目录

1. [迁移前准备](#迁移前准备)
2. [环境准备](#环境准备)
3. [代码迁移](#代码迁移)
4. [数据库迁移](#数据库迁移)
5. [配置文件检查](#配置文件检查)
6. [依赖安装](#依赖安装)
7. [启动验证](#启动验证)
8. [常见问题](#常见问题)

---

## 迁移前准备

### 1. 备份当前数据

在源电脑上执行以下备份操作：

#### 备份数据库

```bash
# 如果使用 Docker Compose 启动的 PostgreSQL
docker exec platform-postgres pg_dump -U platform -d workflow_platform > workflow_platform_backup.sql

# 或者使用 psql 命令（如果直接连接）
pg_dump -h localhost -U platform -d workflow_platform > workflow_platform_backup.sql
```

**Windows PowerShell 示例**:
```powershell
docker exec platform-postgres pg_dump -U platform -d workflow_platform | Out-File -FilePath workflow_platform_backup.sql -Encoding utf8
```

#### 备份重要文件

- ✅ 数据库备份文件：`workflow_platform_backup.sql`
- ✅ 代码仓库：整个项目目录
- ✅ 配置文件：如果有自定义配置
- ⚠️ **不要** 备份 `node_modules/`、`target/`、`.git/`（这些可以重新生成）

### 2. 准备传输工具

- **方式 1**: U盘或移动硬盘
- **方式 2**: 网络共享（SMB/FTP）
- **方式 3**: Git 仓库（推荐，如果代码已提交到 Git）

---

## 环境准备

在目标 Windows 电脑上安装以下软件：

### 必需软件

#### 1. Java 17 或更高版本

**下载地址**: https://adoptium.net/

**安装步骤**:
1. 下载 Windows x64 安装程序（JDK 17）
2. 运行安装程序，选择安装路径（建议：`C:\Program Files\Java\jdk-17`）
3. 配置环境变量：
   ```powershell
   # 打开系统环境变量设置
   [Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-17", "Machine")
   [Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\Java\jdk-17\bin", "Machine")
   ```
4. 验证安装：
   ```powershell
   java -version
   javac -version
   ```

#### 2. Maven 3.9 或更高版本

**下载地址**: https://maven.apache.org/download.cgi

**安装步骤**:
1. 下载 `apache-maven-3.9.x-bin.zip`
2. 解压到 `C:\Program Files\Apache\maven`
3. 配置环境变量：
   ```powershell
   [Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\Program Files\Apache\maven", "Machine")
   [Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\Apache\maven\bin", "Machine")
   ```
4. 验证安装：
   ```powershell
   mvn -version
   ```

#### 3. Node.js 20 或更高版本

**下载地址**: https://nodejs.org/

**安装步骤**:
1. 下载 Windows 安装程序（LTS 版本，推荐 20.x）
2. 运行安装程序，使用默认选项
3. 验证安装：
   ```powershell
   node -v
   npm -v
   ```

#### 4. Docker Desktop for Windows

**下载地址**: https://www.docker.com/products/docker-desktop/

**安装步骤**:
1. 下载 Docker Desktop Installer
2. 运行安装程序
3. 启动 Docker Desktop，确保启用 WSL 2 后端
4. 验证安装：
   ```powershell
   docker --version
   docker-compose --version
   ```

**重要**: 
- Windows 10/11 需要启用 WSL 2（Windows Subsystem for Linux 2）
- Docker Desktop 会自动提示安装 WSL 2（如果未安装）

#### 5. Git（可选，但推荐）

**下载地址**: https://git-scm.com/download/win

**安装步骤**:
1. 下载 Git for Windows
2. 运行安装程序，使用默认选项
3. 验证安装：
   ```powershell
   git --version
   ```

### 可选软件

- **IDE**: IntelliJ IDEA 或 VS Code（用于开发）
- **数据库客户端**: pgAdmin 4 或 DBeaver（用于数据库管理）

---

## 代码迁移

### 方式 1: 使用 Git（推荐）

如果代码已提交到 Git 仓库：

```powershell
# 克隆仓库
git clone <repository-url>
cd Workflow-Station---sun

# 切换到正确的分支（如果有）
git checkout main  # 或 master
```

### 方式 2: 直接复制文件

1. 将整个项目目录复制到目标电脑
2. 建议放置位置：`C:\Projects\Workflow-Station---sun` 或 `D:\Projects\Workflow-Station---sun`

**注意事项**:
- ⚠️ 不要复制以下目录（会重新生成）：
  - `node_modules/`（前端依赖）
  - `target/`（Maven 编译输出）
  - `.git/`（如果不想保留 Git 历史）
  - `logs/`（日志文件）
  - `backend/*/target/`（各模块编译输出）

### 方式 3: 压缩包传输

1. 在源电脑上，创建压缩包（排除不必要的文件）：
   ```powershell
   # PowerShell 示例（在项目根目录执行）
   Compress-Archive -Path .\* -DestinationPath ..\workflow-platform-migration.zip -Exclude @('node_modules', 'target', '.git', 'logs')
   ```

2. 将压缩包传输到目标电脑

3. 解压到目标位置

---

## 数据库迁移

### 方法 1: 使用数据库备份文件（推荐）

#### 步骤 1: 在目标电脑启动 PostgreSQL

```powershell
# 确保 Docker Desktop 正在运行
docker-compose up -d postgres

# 等待数据库就绪（约 10-30 秒）
docker-compose ps
```

#### 步骤 2: 导入数据库备份

将备份文件 `workflow_platform_backup.sql` 放到项目根目录，然后执行：

```powershell
# 方法 1: 使用 Docker exec
Get-Content workflow_platform_backup.sql | docker exec -i platform-postgres psql -U platform -d workflow_platform

# 方法 2: 使用 psql（如果本地安装了 PostgreSQL 客户端）
psql -h localhost -U platform -d workflow_platform -f workflow_platform_backup.sql
```

**如果遇到权限问题**:

```powershell
# 检查数据库是否已创建
docker exec platform-postgres psql -U platform -c "\l"

# 如果需要重新创建数据库（会丢失现有数据）
docker exec platform-postgres psql -U platform -c "DROP DATABASE IF EXISTS workflow_platform;"
docker exec platform-postgres psql -U platform -c "CREATE DATABASE workflow_platform;"

# 然后再导入
Get-Content workflow_platform_backup.sql | docker exec -i platform-postgres psql -U platform -d workflow_platform
```

### 方法 2: 使用 Flyway 重新初始化（如果没有数据需要保留）

如果目标环境是全新安装，可以直接使用 Flyway 迁移脚本：

1. 确保 `docker-compose.yml` 中配置了初始化脚本
2. 删除现有数据库卷（如果存在）：
   ```powershell
   docker-compose down -v  # 这会删除所有数据卷
   ```
3. 重新启动服务，Flyway 会自动执行迁移脚本

### 验证数据库迁移

```powershell
# 连接到数据库检查
docker exec -it platform-postgres psql -U platform -d workflow_platform

# 检查表数量
\dt

# 检查关键表的数据
SELECT COUNT(*) FROM sys_users;
SELECT COUNT(*) FROM sys_roles;
SELECT COUNT(*) FROM sys_virtual_groups;
SELECT COUNT(*) FROM sys_business_units;

# 退出
\q
```

---

## 配置文件检查

### 1. 检查数据库连接配置

确保所有 `application.yml` 文件中的数据库配置正确：

**文件位置**:
- `backend/api-gateway/src/main/resources/application.yml`
- `backend/admin-center/src/main/resources/application.yml`
- `backend/user-portal/src/main/resources/application.yml`
- `backend/workflow-engine-core/src/main/resources/application.yml`
- `backend/developer-workstation/src/main/resources/application.yml`

**默认配置**（通常不需要修改）:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_platform
    username: platform
    password: platform123  # 或从环境变量读取
```

### 2. 检查 Docker Compose 配置

检查 `docker-compose.yml` 中的端口映射是否与现有服务冲突：

- PostgreSQL: `5432:5432`
- Redis: `6379:6379`
- Kafka: `9092:9092`
- API Gateway: `8080:8080`
- 等等...

### 3. 检查环境变量

如果需要修改默认密码，可以创建 `.env` 文件（在项目根目录）：

```env
POSTGRES_PASSWORD=your_password_here
REDIS_PASSWORD=your_redis_password_here
```

或者使用 PowerShell 设置环境变量：

```powershell
$env:POSTGRES_PASSWORD = "your_password_here"
$env:REDIS_PASSWORD = "your_redis_password_here"
```

---

## 依赖安装

### 1. 安装后端依赖（Maven）

在项目根目录执行：

```powershell
# 进入项目目录
cd C:\Projects\Workflow-Station---sun

# 清理并安装所有模块（跳过测试以加快速度）
mvn clean install -DskipTests
```

**注意**: 首次安装可能需要 10-30 分钟，因为需要下载所有依赖。

### 2. 安装前端依赖（npm）

分别为三个前端应用安装依赖：

```powershell
# Frontend Admin Center
cd frontend/admin-center
npm install

# Frontend User Portal
cd ..\user-portal
npm install

# Frontend Developer Workstation
cd ..\developer-workstation
npm install
```

---

## 启动验证

### 方法 1: 使用 PowerShell 脚本（推荐）

项目已提供 Windows PowerShell 启动脚本：

```powershell
# 在项目根目录执行
.\start-all.ps1
```

脚本会：
1. 检查 Docker 是否运行
2. 启动基础设施服务（PostgreSQL, Redis, Kafka）
3. 启动后端服务（如果选择 Docker 方式）
4. 启动前端服务（如果选择 Docker 方式）

### 方法 2: 使用 Docker Compose

#### 启动基础设施服务

```powershell
docker-compose up -d postgres redis zookeeper kafka
```

等待服务就绪（约 30 秒）：

```powershell
docker-compose ps
```

#### 启动所有服务

```powershell
# 启动所有服务（包括前后端）
docker-compose --profile full up -d

# 或者只启动后端
docker-compose --profile backend up -d

# 或者只启动前端
docker-compose --profile frontend up -d
```

### 方法 3: 本地开发模式（分步启动）

#### 启动基础设施

```powershell
docker-compose up -d postgres redis zookeeper kafka
```

#### 启动后端服务（每个服务需要单独终端）

**终端 1 - API Gateway**:
```powershell
cd backend/api-gateway
mvn spring-boot:run
```

**终端 2 - Workflow Engine**:
```powershell
cd backend/workflow-engine-core
mvn spring-boot:run
```

**终端 3 - Admin Center**:
```powershell
cd backend/admin-center
mvn spring-boot:run
```

**终端 4 - User Portal**:
```powershell
cd backend/user-portal
mvn spring-boot:run
```

**终端 5 - Developer Workstation**:
```powershell
cd backend/developer-workstation
mvn spring-boot:run
```

#### 启动前端服务（每个应用需要单独终端）

**终端 6 - Frontend Admin**:
```powershell
cd frontend/admin-center
npm run dev
```

**终端 7 - Frontend Portal**:
```powershell
cd frontend/user-portal
npm run dev
```

**终端 8 - Frontend Developer**:
```powershell
cd frontend/developer-workstation
npm run dev
```

### 验证服务状态

#### 检查 Docker 服务

```powershell
docker-compose ps
```

应该看到以下服务状态为 `Up`:
- `platform-postgres` - 健康检查通过
- `platform-redis` - 健康检查通过
- `platform-zookeeper` - 运行中
- `platform-kafka` - 运行中
- 其他后端/前端服务（如果已启动）

#### 检查服务端口

```powershell
# 检查端口占用
netstat -ano | findstr :5432  # PostgreSQL
netstat -ano | findstr :6379  # Redis
netstat -ano | findstr :8080  # API Gateway
netstat -ano | findstr :8081  # Workflow Engine
netstat -ano | findstr :8090  # Admin Center
netstat -ano | findstr :8082  # User Portal
netstat -ano | findstr :8083  # Developer Workstation
netstat -ano | findstr :3000  # Frontend Admin
netstat -ano | findstr :3001  # Frontend Portal
netstat -ano | findstr :3002  # Frontend Developer
```

#### 访问前端应用

打开浏览器访问：

- Admin Center: http://localhost:3000
- User Portal: http://localhost:3001
- Developer Workstation: http://localhost:3002

#### 检查后端 API

- API Gateway Swagger: http://localhost:8080/swagger-ui.html
- Workflow Engine Swagger: http://localhost:8081/swagger-ui.html
- Admin Center Swagger: http://localhost:8090/swagger-ui.html

---

## 常见问题

### 1. Docker Desktop 无法启动

**问题**: WSL 2 未安装或未启用

**解决方案**:
```powershell
# 以管理员身份运行 PowerShell
wsl --install

# 重启电脑

# 验证 WSL 2
wsl --list --verbose
```

### 2. 端口已被占用

**问题**: 某些端口（如 5432, 6379）已被其他程序占用

**解决方案**:
- **选项 1**: 停止占用端口的程序
- **选项 2**: 修改 `docker-compose.yml` 中的端口映射（例如：`5433:5432`）
- **选项 3**: 查找并停止占用端口的进程：
  ```powershell
  # 查找占用 5432 端口的进程
  netstat -ano | findstr :5432
  
  # 杀死进程（替换 PID 为实际进程ID）
  taskkill /PID <PID> /F
  ```

### 3. Maven 下载依赖慢

**解决方案**:
配置国内镜像源，编辑 `C:\Users\<用户名>\.m2\settings.xml`（如果不存在则创建）：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

### 4. npm 安装依赖慢

**解决方案**:
配置国内镜像源：

```powershell
npm config set registry https://registry.npmmirror.com
```

### 5. 数据库连接失败

**问题**: 后端服务无法连接到 PostgreSQL

**检查步骤**:
1. 确认 PostgreSQL 容器正在运行：
   ```powershell
   docker ps | findstr postgres
   ```

2. 检查数据库健康状态：
   ```powershell
   docker exec platform-postgres pg_isready -U platform
   ```

3. 检查数据库配置：
   - `application.yml` 中的数据库 URL 是否正确
   - 用户名密码是否匹配

4. 检查防火墙：确保 Docker 的网络连接未被阻止

### 6. 前端页面无法加载

**问题**: 前端应用启动但页面空白或报错

**检查步骤**:
1. 检查浏览器控制台是否有错误
2. 检查后端 API 是否可访问
3. 检查网络请求是否被 CORS 阻止
4. 清除浏览器缓存

### 7. 文件路径问题（Windows vs Linux）

**问题**: 某些脚本或配置文件使用 Linux 路径分隔符

**解决方案**:
- PowerShell 脚本使用 `/` 或 `\` 都可以正常工作
- Java 配置中，文件路径应使用 `/` 或 `\\`（双反斜杠）
- 如果使用 Git Bash，路径分隔符会自动转换

---

## 快速检查清单

迁移完成后，使用以下清单验证系统是否正常运行：

- [ ] Java 17+ 已安装并配置
- [ ] Maven 3.9+ 已安装并配置
- [ ] Node.js 20+ 已安装
- [ ] Docker Desktop 正在运行
- [ ] 项目代码已迁移
- [ ] 数据库已导入并验证
- [ ] 后端依赖已安装（`mvn clean install`）
- [ ] 前端依赖已安装（`npm install`）
- [ ] 基础设施服务已启动（PostgreSQL, Redis, Kafka）
- [ ] 后端服务已启动（至少一个服务）
- [ ] 前端应用已启动（至少一个应用）
- [ ] 可以访问前端页面（http://localhost:3000/3001/3002）
- [ ] 可以访问 Swagger API 文档

---

## 迁移后优化

### 1. 性能优化

- **Maven**: 配置本地仓库缓存
- **npm**: 使用 npm cache 加速安装
- **Docker**: 配置镜像加速器（国内用户）

### 2. 开发工具配置

- **IDE**: 配置代码格式化规则
- **Git**: 配置用户信息
- **终端**: 使用 Windows Terminal 或 PowerShell 7+

### 3. 监控和日志

- 配置日志输出目录
- 设置日志轮转策略
- 配置健康检查端点

---

## 获取帮助

如果遇到问题，请检查：

1. **日志文件**: `logs/` 目录下的日志文件
2. **Docker 日志**: `docker-compose logs [service-name]`
3. **文档**: `docs/` 目录下的相关文档
4. **可执行脚本**: 参考 `docs/executable-scripts-list.md`

---

## 备份建议

定期备份：
- 数据库备份（每周）
- 代码仓库（提交到 Git）
- 配置文件（如果有自定义配置）

---

**祝迁移顺利！** 🚀
