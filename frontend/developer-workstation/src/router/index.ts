import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import i18n from '@/i18n'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { titleKey: 'login.title' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/function-units',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'function-units',
        name: 'FunctionUnits',
        component: () => import('@/views/function-unit/FunctionUnitList.vue'),
        meta: { titleKey: 'functionUnit.title' }
      },
      {
        path: 'function-units/:id',
        name: 'FunctionUnitEdit',
        component: () => import('@/views/function-unit/FunctionUnitEdit.vue'),
        meta: { titleKey: 'functionUnit.edit' }
      },
      {
        path: 'icons',
        name: 'IconLibrary',
        component: () => import('@/views/icon/IconLibrary.vue'),
        meta: { titleKey: 'icon.title' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const t = i18n.global.t
  const pageTitle = (to.meta as any)?.titleKey ? t((to.meta as any).titleKey) : ((to.meta as any)?.title || t('app.name'))
  document.title = `${pageTitle} - ${t('app.title')}`
  
  console.log('[Router] Navigating to:', to.path, 'from:', _from.path)
  
  // 如果是登录页，检查是否已登录
  if (to.path === '/login') {
    console.log('[Router] Login page, checking auth...')
    const token = localStorage.getItem('token')
    if (token) {
      // 已登录，重定向到首页
      console.log('[Router] Token found, checking user...')
      const { getUser } = await import('@/api/auth')
      const user = getUser()
      if (user) {
        console.log('[Router] User found, redirecting to home')
        next('/')
        return
      }
    }
    // 未登录，显示登录页
    console.log('[Router] No token or user, showing login page')
    next()
    return
  }
  
  // 检查需要认证的路由
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
  if (requiresAuth) {
    console.log('[Router] Route requires auth, checking...')
    // 检查登录状态
    const token = localStorage.getItem('token')
    if (!token) {
      // 没有 token，重定向到登录页
      console.log('[Router] No token, redirecting to login')
      next({
        path: '/login',
        query: { redirect: to.fullPath } // 保存原始路径，登录后可以重定向回来
      })
      return
    }
    
    // 如果已登录但用户信息不存在，尝试获取用户信息
    const { getUser } = await import('@/api/auth')
    const user = getUser()
    if (!user) {
      // 用户信息不存在，可能是 token 过期或无效，清除并重定向到登录页
      console.log('[Router] No user info, clearing auth and redirecting to login')
      const { clearAuth } = await import('@/api/auth')
      clearAuth()
      next({
        path: '/login',
        query: { redirect: to.fullPath }
      })
      return
    }
    console.log('[Router] Auth OK, proceeding')
  }
  
  next()
})

export default router
