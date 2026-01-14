# 调试登录界面不显示问题

## 问题现象

访问 http://localhost:3002 时，登录界面不显示。

## 可能的原因

1. **端口问题**：服务可能运行在其他端口（如 3004）
2. **路由守卫问题**：路由守卫可能阻止了登录页显示
3. **组件加载问题**：Login 组件可能没有正确加载
4. **浏览器缓存**：浏览器可能缓存了旧版本

## 调试步骤

### 1. 检查服务运行端口

```bash
# 查看前端服务日志
tail -f logs/frontend-developer.log

# 或者检查端口占用
lsof -i :3002
lsof -i :3003
lsof -i :3004
```

### 2. 检查浏览器控制台

打开浏览器开发者工具 (F12)，检查：
- Console 标签：查看是否有 JavaScript 错误
- Network 标签：查看请求是否成功
- Application 标签：检查 localStorage 中是否有 token

### 3. 清除浏览器缓存

1. 打开开发者工具 (F12)
2. 右键点击刷新按钮
3. 选择"清空缓存并硬性重新加载"
4. 或者在 Application → Storage → Clear site data

### 4. 直接访问登录页

尝试直接访问：http://localhost:3002/login

### 5. 检查路由配置

在浏览器控制台输入：
```javascript
// 检查当前路由
console.log(window.location.pathname)

// 检查 localStorage
console.log(localStorage.getItem('token'))
console.log(localStorage.getItem('user'))

// 清除所有认证信息
localStorage.removeItem('token')
localStorage.removeItem('refreshToken')
localStorage.removeItem('user')
localStorage.removeItem('userId')
location.reload()
```

## 修复方案

### 方案 1: 重启前端服务

```bash
cd Workflow-Station---sun
./stop-frontend.sh
./start-frontend.sh
```

### 方案 2: 检查服务端口

如果服务运行在 3004 端口，访问：http://localhost:3004

### 方案 3: 手动启动前端服务

```bash
cd Workflow-Station---sun/frontend/developer-workstation
npm run dev
```

查看输出，确认实际运行的端口。

## 验证修复

1. 清除浏览器缓存和 localStorage
2. 访问 http://localhost:3002（或实际运行的端口）
3. 应该看到登录页面，包含：
   - "开发者工作站" 标题
   - 测试用户快速选择下拉框（开发环境）
   - 用户名和密码输入框
   - 登录按钮

## 如果仍然无法显示

1. 检查浏览器控制台的完整错误信息
2. 检查网络请求，确认是否有 404 或其他错误
3. 检查 Vite 开发服务器的输出日志
4. 尝试在无痕模式下访问
