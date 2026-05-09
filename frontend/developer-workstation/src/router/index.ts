import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { hasAnyRole } from '@/utils/permission'
import i18n from '@/i18n'
import { redirectToUnifiedLogin, setSsoReturnPath } from '@/utils/sso'
import { TOKEN_KEY } from '@/api/auth'

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
    path: '/sso/callback',
    name: 'SsoCallback',
    component: () => import('@/views/SsoCallback.vue'),
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
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const t = i18n.global.t
  const pageTitle = (to.meta as any)?.titleKey ? t((to.meta as any).titleKey) : ((to.meta as any)?.title || t('app.name'))
  document.title = `${pageTitle} - ${t('app.title')}`

  if (to.path === '/login') {
    const r = to.query.redirect
    setSsoReturnPath(typeof r === 'string' ? r : '/')
    redirectToUnifiedLogin('developer-workstation')
    return next(false)
  }

  if (to.path === '/sso/callback') {
    const token = localStorage.getItem(TOKEN_KEY)
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
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) {
      setSsoReturnPath(to.fullPath)
      redirectToUnifiedLogin('developer-workstation')
      return next(false)
    }

    const { getUser } = await import('@/api/auth')
    const user = getUser()
    if (!user) {
      const { clearAuth } = await import('@/api/auth')
      clearAuth()
      setSsoReturnPath(to.fullPath)
      redirectToUnifiedLogin('developer-workstation')
      return next(false)
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
