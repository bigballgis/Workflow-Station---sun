import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { hasAnyRole } from '@/utils/permission'
import i18n from '@/i18n'

declare module 'vue-router' {
  interface RouteMeta {
    titleKey?: string
    title?: string
    icon?: string
    hidden?: boolean
    requiresAuth?: boolean
    requiredRoles?: string[]
  }
}

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
        meta: { titleKey: 'functionUnit.title', requiredRoles: ['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'] }
      },
      {
        path: 'function-units/:id',
        name: 'FunctionUnitEdit',
        component: () => import('@/views/function-unit/FunctionUnitEdit.vue'),
        meta: { titleKey: 'functionUnit.edit', requiredRoles: ['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'] }
      },
      {
        path: 'icons',
        name: 'IconLibrary',
        component: () => import('@/views/icon/IconLibrary.vue'),
        meta: { titleKey: 'icon.title' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { titleKey: 'profile.title', hidden: true }
      },
      {
        path: '403',
        name: 'Forbidden',
        component: () => import('@/views/error/403.vue'),
        meta: { titleKey: 'error.forbidden', hidden: true }
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
  
  // 如果是登录页，检查是否已登录
  if (to.path === '/login') {
    const token = localStorage.getItem('token')
    if (token) {
      const { getUser } = await import('@/api/auth')
      const user = getUser()
      if (user) {
        next('/')
        return
      }
    }
    next()
    return
  }
  
  // 检查需要认证的路由
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
  if (requiresAuth) {
    const token = localStorage.getItem('token')
    if (!token) {
      next({
        path: '/login',
        query: { redirect: to.fullPath }
      })
      return
    }
    
    const { getUser } = await import('@/api/auth')
    const user = getUser()
    if (!user) {
      const { clearAuth } = await import('@/api/auth')
      clearAuth()
      next({
        path: '/login',
        query: { redirect: to.fullPath }
      })
      return
    }
  }

  const requiredRoles = to.meta.requiredRoles as string[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    if (!hasAnyRole(requiredRoles)) {
      next('/403')
      return
    }
  }
  
  next()
})

export default router
