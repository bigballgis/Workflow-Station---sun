# 登录界面不显示 - 故障排查指南

## 快速检查清单

### 1. 确认服务运行端口

从日志看，服务可能运行在 3004 端口而不是 3002。请检查：

```bash
# 查看服务日志
tail -f logs/frontend-developer.log

# 查找实际运行的端口
grep -E "Local:|Network:" logs/frontend-developer.log
```

**解决方案**：
- 如果服务运行在 3004，访问 http://localhost:3004
- 或者停止占用 3002 端口的进程，重启服务

### 2. 清除浏览器缓存

1. 打开浏览器开发者工具 (F12)
2. 右键点击刷新按钮
3. 选择"清空缓存并硬性重新加载"
4. 或者在 Application → Storage → Clear site data

### 3. 检查浏览器控制台

打开浏览器开发者工具 (F12)，检查：
- **Console 标签**：查看是否有 JavaScript 错误
- **Network 标签**：查看请求是否成功，特别是 `/src/main.ts` 和 `/src/views/Login.vue`
- **Application 标签**：检查 localStorage

### 4. 直接访问登录页

尝试直接访问：http://localhost:3002/login

如果仍然不显示，尝试：
- http://localhost:3003/login
- http://localhost:3004/login

### 5. 手动清除 localStorage

在浏览器控制台执行：

```javascript
// 清除所有认证信息
localStorage.clear()
// 或者只清除相关项
localStorage.removeItem('token')
localStorage.removeItem('refreshToken')
localStorage.removeItem('user')
localStorage.removeItem('userId')
// 刷新页面
location.reload()
```

### 6. 检查路由配置

在浏览器控制台执行：

```javascript
// 检查当前路由
console.log(window.location.pathname)
console.log(window.location.href)

// 检查 Vue Router 实例
console.log(window.__VUE_ROUTER__)
```

## 常见问题

### 问题 1: 服务运行在其他端口

**现象**：访问 3002 无响应，但日志显示服务在 3004

**解决**：
1. 访问实际运行的端口（如 http://localhost:3004）
2. 或者停止占用端口的进程：
   ```bash
   lsof -ti:3002 | xargs kill -9
   lsof -ti:3003 | xargs kill -9
   ```
3. 重启前端服务

### 问题 2: 路由守卫阻止显示

**现象**：页面空白，控制台无错误

**解决**：
1. 清除 localStorage 中的 token
2. 刷新页面
3. 应该自动重定向到登录页

### 问题 3: 组件加载失败

**现象**：控制台有 404 错误

**解决**：
1. 检查 Vite 开发服务器是否正常运行
2. 检查文件路径是否正确
3. 重启开发服务器

### 问题 4: 浏览器缓存问题

**现象**：修改代码后页面不更新

**解决**：
1. 硬刷新：Ctrl+Shift+R (Windows) 或 Cmd+Shift+R (Mac)
2. 清除浏览器缓存
3. 使用无痕模式访问

## 调试命令

### 检查服务状态

```bash
# 查看前端服务进程
ps aux | grep -E "vite|npm.*dev" | grep developer

# 查看端口占用
lsof -i :3002
lsof -i :3003
lsof -i :3004

# 查看服务日志
tail -f logs/frontend-developer.log
```

### 重启服务

```bash
cd Workflow-Station---sun
./stop-frontend.sh
./start-frontend.sh
```

### 手动启动（用于调试）

```bash
cd Workflow-Station---sun/frontend/developer-workstation
npm run dev
```

查看输出，确认：
- 实际运行的端口
- 是否有编译错误
- 是否有模块加载错误

## 验证步骤

1. ✅ 服务正常运行（检查日志）
2. ✅ 浏览器能访问服务（检查 Network 标签）
3. ✅ 没有 JavaScript 错误（检查 Console 标签）
4. ✅ localStorage 中没有 token（检查 Application 标签）
5. ✅ 路由正确（检查 URL 是否为 /login）

## 如果仍然无法解决

1. 提供完整的浏览器控制台错误信息
2. 提供 Network 标签中的请求详情
3. 提供服务日志的完整输出
4. 说明使用的浏览器和版本
