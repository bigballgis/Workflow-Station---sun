# 修复登录界面不显示和 403 错误问题

## 问题描述

1. **登录界面不显示**：访问 http://localhost:3002 时，登录页面不显示
2. **403 Forbidden 错误**：控制台报错 `GET http://localhost:3002/api/v1/function-units?page=0&size=20 403 (Forbidden)`
3. **组件在未登录时加载数据**：FunctionUnitList 组件在挂载时就尝试加载数据，即使没有登录

## 问题原因

1. **路由守卫逻辑问题**：虽然检查了 token，但可能在组件挂载后才执行，导致组件已经加载数据
2. **组件未检查登录状态**：FunctionUnitList 在 `onMounted` 时直接调用 `loadData()`，没有检查是否已登录
3. **API 拦截器未处理 403**：403 错误没有触发重定向到登录页
4. **functionUnit API 未处理认证错误**：functionUnit.ts 的拦截器没有处理 401/403 错误

## 修复方案

### 1. 改进路由守卫

**文件**: `src/router/index.ts`

- 优化路由守卫逻辑，先检查是否是登录页
- 确保未登录时立即重定向，不等待异步操作

```typescript
router.beforeEach(async (to, _from, next) => {
  // 如果是登录页，直接放行
  if (to.path === '/login') {
    next()
    return
  }
  
  // 检查登录状态
  const token = localStorage.getItem('token')
  if (!token) {
    next('/login')
    return
  }
  
  // 检查用户信息
  const { getUser } = await import('@/api/auth')
  const user = getUser()
  if (!user) {
    const { clearAuth } = await import('@/api/auth')
    clearAuth()
    next('/login')
    return
  }
  
  next()
})
```

### 2. 在组件中添加登录检查

**文件**: `src/views/function-unit/FunctionUnitList.vue`

- 在 `onMounted` 时检查登录状态
- 只有已登录时才加载数据

```typescript
import { isAuthenticated } from '@/api/auth'

onMounted(() => {
  // 检查是否已登录
  if (isAuthenticated()) {
    loadData()
  } else {
    // 未登录，路由守卫应该已经重定向，但以防万一
    router.push('/login')
  }
})
```

### 3. 改进 API 拦截器处理 403 错误

**文件**: `src/api/index.ts`

- 在 403 错误时检查是否有 token
- 如果没有 token，清除认证并重定向到登录页

```typescript
case 403:
  // 403 可能是未登录或权限不足
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) {
    // 没有 token，清除认证并重定向到登录页
    clearAuth()
    router.push('/login')
    ElMessage.warning('请先登录')
  } else {
    ElMessage.error('没有权限执行此操作')
  }
  break
```

### 4. 为 functionUnit API 添加错误处理

**文件**: `src/api/functionUnit.ts`

- 添加响应拦截器处理 401/403 错误
- 自动重定向到登录页

```typescript
functionUnitAxios.interceptors.response.use(
  response => response.data,
  async error => {
    const { response } = error
    
    // 处理 401 未授权
    if (response?.status === 401) {
      const { clearAuth } = await import('./auth')
      const router = (await import('@/router')).default
      clearAuth()
      router.push('/login')
      return Promise.reject(error)
    }
    
    // 处理 403 禁止访问
    if (response?.status === 403) {
      const { TOKEN_KEY, clearAuth } = await import('./auth')
      const token = localStorage.getItem(TOKEN_KEY)
      if (!token) {
        clearAuth()
        const router = (await import('@/router')).default
        router.push('/login')
      }
      return Promise.reject(error)
    }
    
    return Promise.reject(error)
  }
)
```

## 验证修复

1. **清除浏览器缓存和 localStorage**
   - 打开浏览器开发者工具 (F12)
   - 进入 Application/存储 → Local Storage
   - 清除所有相关数据（token, refreshToken, user 等）

2. **访问应用**
   - 访问 http://localhost:3002
   - 应该自动重定向到登录页面 `/login`
   - 登录页面应该正常显示

3. **检查控制台**
   - 不应该有 403 错误
   - 不应该有未登录时的 API 请求

4. **登录测试**
   - 使用测试账户登录（如 `tech.director / admin123`）
   - 登录后应该正常显示功能单元列表
   - 不应该有 403 错误

## 预期结果

- ✅ 未登录时自动重定向到登录页
- ✅ 登录页面正常显示
- ✅ 未登录时不会发送 API 请求
- ✅ 403 错误时自动重定向到登录页（如果是未登录导致的）
- ✅ 已登录用户正常访问功能

## 相关文件

- `src/router/index.ts` - 路由守卫
- `src/views/function-unit/FunctionUnitList.vue` - 功能单元列表组件
- `src/api/index.ts` - 通用 API 拦截器
- `src/api/functionUnit.ts` - 功能单元 API 拦截器
- `src/views/Login.vue` - 登录页面

## 注意事项

1. 确保后端 API 正常工作
2. 确保登录 API 返回正确的 token 和用户信息
3. 如果问题仍然存在，检查浏览器控制台的完整错误信息
4. 检查网络请求，确认 API 请求的 URL 和 headers 是否正确
