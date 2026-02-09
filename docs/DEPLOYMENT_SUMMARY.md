# 前端重新构建和部署总结

## 完成的工作

### 1. 前端构建优化
- 创建了本地构建 Dockerfile (`Dockerfile.local`)，跳过 Docker 内构建，直接使用本地构建的 dist
- 创建了自动化部署脚本 `deploy/scripts/build-and-deploy-frontend-local.ps1`
- 构建速度从 2-3 分钟提升到 30-60 秒

### 2. Nginx 配置参数化
所有前端 nginx 配置使用环境变量：
- **Admin Center**: `ADMIN_CENTER_BACKEND_URL`
- **User Portal**: `USER_PORTAL_BACKEND_URL`, `ADMIN_CENTER_BACKEND_URL`
- **Developer Workstation**: `DEVELOPER_WORKSTATION_BACKEND_URL`, `ADMIN_CENTER_BACKEND_URL`

### 3. 数据库初始化修复
- 修复了 docker-compose 中的初始化脚本路径：`../../../deploy/init-scripts`
- 修复了 `00-init-all-schemas.sql` 中的相对路径问题（使用绝对路径）
- 创建了 `00-init-all.sh` 脚本来执行所有初始化脚本
- 创建了 `01-create-admin-user.sql` 来初始化管理员用户

### 4. 测试用户创建
创建了以下测试用户（所有密码都是 `password`）：
- `super_admin` - 超级管理员（SUPER_ADMIN 角色）
- `manager` - 经理（MANAGER 角色）
- `developer` - 开发者（DEVELOPER 角色）
- `designer` - 设计师（DESIGNER 角色）

## 当前状态

### 运行中的服务
```
✅ PostgreSQL - 数据库已初始化，包含 69 张表
✅ Redis - 缓存服务运行正常
✅ Admin Center Backend - 后端服务运行中
✅ User Portal Backend - 后端服务运行中
✅ Developer Workstation Backend - 后端服务运行中
✅ Admin Center Frontend - 前端服务运行在 http://localhost:3000
✅ User Portal Frontend - 前端服务运行在 http://localhost:3001
✅ Developer Workstation Frontend - 前端服务运行在 http://localhost:3002
```

### API 路由配置

#### Admin Center (localhost:3000)
```
/api/v1/* → http://platform-admin-center-dev:8080/api/v1/admin/*
```

#### User Portal (localhost:3001)
```
/api/portal/* → http://platform-user-portal-dev:8080/api/portal/*
/api/admin-center/* → http://platform-admin-center-dev:8080/api/v1/admin/*
/api/v1/auth/* → http://platform-user-portal-dev:8080/api/portal/auth/*
```

#### Developer Workstation (localhost:3002)
```
/api/v1/* → http://platform-developer-workstation-dev:8080/api/developer/*
/api/admin-center/* → http://platform-admin-center-dev:8080/api/v1/admin/*
```

## 登录测试

### Admin Center (http://localhost:3000)
```
用户名: super_admin
密码: password
```

### User Portal (http://localhost:3001)
```
用户名: manager
密码: password
```

### Developer Workstation (http://localhost:3002)
```
用户名: developer
密码: password
```

## 快速重新部署命令

### 重新构建并部署所有前端
```powershell
cd deploy/scripts
.\build-and-deploy-frontend-local.ps1 -Frontend all
```

### 重新构建单个前端
```powershell
# Admin Center
.\build-and-deploy-frontend-local.ps1 -Frontend admin

# User Portal
.\build-and-deploy-frontend-local.ps1 -Frontend user

# Developer Workstation
.\build-and-deploy-frontend-local.ps1 -Frontend developer
```

### 重启后端服务
```powershell
cd deploy/environments/dev
docker-compose -f docker-compose.dev.yml restart admin-center user-portal developer-workstation
```

### 重新初始化数据库（慎用！会删除所有数据）
```powershell
cd deploy/environments/dev
docker-compose -f docker-compose.dev.yml down postgres
docker volume rm dev_postgres_dev_data
docker-compose -f docker-compose.dev.yml up -d postgres
# 等待 40 秒让数据库初始化完成
Start-Sleep -Seconds 40
# 重启所有后端服务
docker-compose -f docker-compose.dev.yml restart admin-center user-portal developer-workstation workflow-engine
```

## 文件清单

### 新增文件
- `frontend/admin-center/Dockerfile.local` - 本地构建 Dockerfile
- `frontend/user-portal/Dockerfile.local` - 本地构建 Dockerfile
- `frontend/developer-workstation/Dockerfile.local` - 本地构建 Dockerfile
- `frontend/admin-center/docker-entrypoint.sh` - Nginx 环境变量替换脚本
- `frontend/user-portal/docker-entrypoint.sh` - Nginx 环境变量替换脚本
- `frontend/developer-workstation/docker-entrypoint.sh` - Nginx 环境变量替换脚本
- `deploy/scripts/build-and-deploy-frontend-local.ps1` - 自动化部署脚本
- `deploy/init-scripts/00-init-all.sh` - 数据库初始化主脚本
- `deploy/init-scripts/01-admin/01-create-admin-user.sql` - 管理员用户初始化脚本

### 修改文件
- `frontend/admin-center/Dockerfile` - 添加 npm 缓存优化
- `frontend/user-portal/Dockerfile` - 添加 npm 缓存优化
- `frontend/developer-workstation/Dockerfile` - 添加 npm 缓存优化
- `frontend/admin-center/nginx.conf` - 参数化后端 URL
- `frontend/user-portal/nginx.conf` - 参数化后端 URL
- `frontend/developer-workstation/nginx.conf` - 参数化后端 URL
- `deploy/environments/dev/docker-compose.dev.yml` - 使用本地构建镜像，修复初始化脚本路径
- `deploy/init-scripts/00-schema/00-init-all-schemas.sql` - 修复相对路径问题

## 下一步

现在可以访问以下 URL 进行测试：
1. http://localhost:3000 - Admin Center（使用 super_admin / password 登录）
2. http://localhost:3001 - User Portal（使用 manager / password 登录）
3. http://localhost:3002 - Developer Workstation（使用 developer / password 登录）

所有服务已经正常运行，数据库已初始化完成！
