# 修复 "一进去就是Developer角色" 问题

## 问题描述

访问 http://localhost:3002（开发者工作站前端）时，页面右上角显示的用户名是 "Developer"，而不是实际登录用户的名称。

## 问题原因

在 `src/layouts/MainLayout.vue` 第 80 行，有一个硬编码的默认值：

```typescript
const displayName = computed(() => currentUser.value?.displayName || currentUser.value?.username || 'Developer')
```

当用户信息没有正确加载时（比如 localStorage 中没有用户信息，或者用户信息格式不对），就会显示默认值 'Developer'。

可能的原因：
1. 用户没有登录，直接访问了页面
2. 登录后用户信息没有正确保存到 localStorage
3. 用户信息格式不对，导致 `getUser()` 返回 null
4. Token 存在但用户信息丢失

## 修复方案

### 1. 修改默认显示名称

将硬编码的 'Developer' 改为 '未登录'，更清楚地提示用户状态。

**文件**: `src/layouts/MainLayout.vue`

```typescript
const displayName = computed(() => {
  if (currentUser.value?.displayName) {
    return currentUser.value.displayName
  }
  if (currentUser.value?.username) {
    return currentUser.value.username
  }
  // 如果没有用户信息，可能是未登录，返回提示
  return '未登录'
})
```

### 2. 改进路由守卫

在路由守卫中检查用户信息，如果 token 存在但用户信息不存在，尝试获取或重定向到登录页。

**文件**: `src/router/index.ts`

```typescript
router.beforeEach(async (to, _from, next) => {
  // ... 检查 token ...
  
  // 如果已登录但用户信息不存在，尝试获取用户信息
  if (to.path !== '/login' && token) {
    const { getUser } = await import('@/api/auth')
    const user = getUser()
    if (!user) {
      // 用户信息不存在，可能是 token 过期或无效，清除并重定向到登录页
      const { clearAuth } = await import('@/api/auth')
      clearAuth()
      next('/login')
      return
    }
  }
  
  next()
})
```

### 3. 在组件挂载时获取用户信息

如果用户信息不存在，尝试从 API 获取。

**文件**: `src/layouts/MainLayout.vue`

```typescript
onMounted(async () => {
  initSidebarState()
  
  // 如果用户信息不存在，尝试从 API 获取
  if (!currentUser.value) {
    try {
      const user = await getCurrentUser()
      if (user) {
        const { saveUser } = await import('@/api/auth')
        saveUser(user)
      }
    } catch (error) {
      console.error('Failed to get current user:', error)
      // 如果获取失败，可能是 token 无效，清除认证信息
      clearAuth()
      router.push('/login')
    }
  }
})
```

## 验证修复

1. **清除浏览器缓存和 localStorage**
   - 打开浏览器开发者工具 (F12)
   - 进入 Application/存储 → Local Storage
   - 清除所有相关数据

2. **重新登录**
   - 访问 http://localhost:3002
   - 使用测试账户登录（如 `tech.director / admin123`）
   - 检查右上角是否显示正确的用户名（如 "Robert Sun" 或 "tech.director"）

3. **检查用户信息**
   - 打开浏览器控制台
   - 输入：`localStorage.getItem('user')`
   - 应该能看到完整的用户信息 JSON

## 预期结果

- ✅ 登录后，右上角显示正确的用户显示名称或用户名
- ✅ 如果未登录，显示 "未登录" 并重定向到登录页
- ✅ 如果 token 无效，自动清除认证信息并重定向到登录页
- ✅ 不再显示硬编码的 "Developer"

## 相关文件

- `src/layouts/MainLayout.vue` - 主布局组件，显示用户信息
- `src/router/index.ts` - 路由守卫，检查认证状态
- `src/api/auth.ts` - 认证 API，管理用户信息

## 注意事项

1. 确保后端 API `/api/v1/auth/me` 正常工作
2. 确保登录时正确保存用户信息到 localStorage
3. 如果问题仍然存在，检查浏览器控制台的错误信息
