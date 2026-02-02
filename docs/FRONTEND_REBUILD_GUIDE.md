# Frontend Nginx Configuration & Build Optimization

## 完成的工作

### 1. Nginx 配置参数化
所有三个前端的 nginx 配置已经使用环境变量参数化：

- **Admin Center**: `ADMIN_CENTER_BACKEND_URL`
- **User Portal**: `USER_PORTAL_BACKEND_URL`, `ADMIN_CENTER_BACKEND_URL`
- **Developer Workstation**: `DEVELOPER_WORKSTATION_BACKEND_URL`, `ADMIN_CENTER_BACKEND_URL`

### 2. Docker 构建优化
所有 Dockerfile 已优化，使用 npm 缓存加速构建：
```dockerfile
RUN --mount=type=cache,target=/root/.npm \
    npm ci --prefer-offline --no-audit
```

### 3. 环境变量配置
docker-compose.dev.yml 已更新，正确的容器名称：
- `platform-admin-center-dev:8080`
- `platform-user-portal-dev:8080`
- `platform-developer-workstation-dev:8080`

## API 路由配置

### Admin Center (localhost:3000)
- `/api/v1/*` → `http://platform-admin-center-dev:8080/api/v1/admin/*`

### User Portal (localhost:3001)
- `/api/portal/*` → `http://platform-user-portal-dev:8080/api/portal/*`
- `/api/admin-center/*` → `http://platform-admin-center-dev:8080/api/v1/admin/*`
- `/api/v1/auth/*` → `http://platform-user-portal-dev:8080/api/portal/auth/*` (legacy)

### Developer Workstation (localhost:3002)
- `/api/v1/*` → `http://platform-developer-workstation-dev:8080/api/developer/*`
- `/api/admin-center/*` → `http://platform-admin-center-dev:8080/api/v1/admin/*`

## 重新构建步骤

### 方式 1: 重建所有前端容器
```powershell
cd deploy/environments/dev
docker-compose down admin-center-frontend user-portal-frontend developer-workstation-frontend
docker-compose build --no-cache admin-center-frontend user-portal-frontend developer-workstation-frontend
docker-compose up -d admin-center-frontend user-portal-frontend developer-workstation-frontend
```

### 方式 2: 单独重建某个前端
```powershell
cd deploy/environments/dev

# Admin Center
docker-compose down admin-center-frontend
docker-compose build --no-cache admin-center-frontend
docker-compose up -d admin-center-frontend

# User Portal
docker-compose down user-portal-frontend
docker-compose build --no-cache user-portal-frontend
docker-compose up -d user-portal-frontend

# Developer Workstation
docker-compose down developer-workstation-frontend
docker-compose build --no-cache developer-workstation-frontend
docker-compose up -d developer-workstation-frontend
```

## 验证步骤

### 1. 检查容器状态
```powershell
docker ps | Select-String "frontend"
```

### 2. 检查 nginx 配置
```powershell
# Admin Center
docker exec platform-admin-center-frontend-dev cat /etc/nginx/conf.d/default.conf

# User Portal
docker exec platform-user-portal-frontend-dev cat /etc/nginx/conf.d/default.conf

# Developer Workstation
docker exec platform-developer-workstation-frontend-dev cat /etc/nginx/conf.d/default.conf
```

### 3. 测试登录
- Admin Center: http://localhost:3000
  - 用户名: `super_admin`
  - 密码: `password`

- User Portal: http://localhost:3001
  - 用户名: `manager`
  - 密码: `password`

- Developer Workstation: http://localhost:3002
  - 用户名: `developer`
  - 密码: `password`

### 4. 检查日志
```powershell
# 查看前端容器日志
docker logs platform-admin-center-frontend-dev
docker logs platform-user-portal-frontend-dev
docker logs platform-developer-workstation-frontend-dev

# 查看后端容器日志
docker logs platform-admin-center-dev
docker logs platform-user-portal-dev
docker logs platform-developer-workstation-dev
```

## 构建速度优化

使用 `--mount=type=cache` 后，第二次构建会快很多：
- 首次构建: ~2-3 分钟
- 后续构建: ~30-60 秒（如果依赖没变）

## 故障排查

### 问题: 405 Not Allowed
检查 nginx 配置中的 proxy_pass 路径是否正确

### 问题: 502 Bad Gateway
检查后端容器是否运行：
```powershell
docker ps | Select-String "platform-"
```

### 问题: 环境变量未生效
检查 docker-entrypoint.sh 是否有执行权限：
```powershell
docker exec platform-admin-center-frontend-dev ls -la /docker-entrypoint.sh
```

### 问题: 构建缓存问题
清除构建缓存：
```powershell
docker builder prune -a
```
