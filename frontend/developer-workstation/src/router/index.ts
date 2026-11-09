import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { DW_ENTRY_ROLES, hasAnyRole } from '@/utils/permission'
import i18n from '@/i18n'
import { redirectToUnifiedLogin, setSsoReturnPath } from '@/utils/sso'
import { COMPUTED_FIELD_GUIDE_ROUTE_NAME } from '@/utils/computedFieldGuide'

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
    // AI Studio 工作台：全屏三栏布局，自带左侧阶段轨道，因此不挂在 MainLayout 下
    path: '/function-units/:id/ai-studio',
    name: 'AiStudioWorkspace',
    component: () => import('@/views/function-unit/AiStudioWorkspace.vue'),
    meta: { titleKey: 'ai.studio.entryButton', requiresAuth: true, requiredRoles: DW_ENTRY_ROLES }
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
        meta: { titleKey: 'functionUnit.title', requiredRoles: DW_ENTRY_ROLES }
      },
      {
        path: 'function-units/:id',
        name: 'FunctionUnitEdit',
        component: () => import('@/views/function-unit/FunctionUnitEdit.vue'),
        meta: { titleKey: 'functionUnit.edit', requiredRoles: DW_ENTRY_ROLES }
      },
      {
        path: 'automation',
        name: 'Automation',
        component: () => import('@/views/automation/AutomationPage.vue'),
        meta: { titleKey: 'automation.title', requiredRoles: DW_ENTRY_ROLES }
      },
      {
        path: 'automation/:flowId',
        name: 'AutomationFlowEdit',
        component: () => import('@/views/automation/AutomationFlowEdit.vue'),
        meta: { titleKey: 'automation.title', hidden: true, requiredRoles: DW_ENTRY_ROLES }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { titleKey: 'profile.title', hidden: true }
      },
      {
        path: 'help/computed-fields',
        name: COMPUTED_FIELD_GUIDE_ROUTE_NAME,
        component: () => import('@/views/help/ComputedFieldGuide.vue'),
        meta: {
          hidden: true
        }
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
    redirectToUnifiedLogin('developer-workstation', { autoSso: true })
    return next(false)
  }

  if (to.path === '/sso/callback') {
    // Auth checked via httpOnly cookie — getUser reads from localStorage user profile
    const { getUser } = await import('@/api/auth')
    const user = getUser()
    if (user) {
      next('/')
      return
    }
    next()
    return
  }
  
  // 检查需要认证的路由
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
  if (requiresAuth) {
    // Auth checked via httpOnly cookie — if no valid cookie, API calls will 401 and redirect to login
    const { getUser } = await import('@/api/auth')
    const user = getUser()
    if (!user) {
      const { clearAuth } = await import('@/api/auth')
      clearAuth()
      setSsoReturnPath(to.fullPath)
      redirectToUnifiedLogin('developer-workstation', { autoSso: true })
      return next(false)
    }
  }

  // Platform access model §6.1: SYS_ADMIN, AUDITOR (read-only) and the Technical Lead / Developer lists of
  // active teams enter; the backend refuses everyone else at sign-in and refresh as well.
  const requiredRoles = to.meta.requiredRoles as string[] | undefined
  if (requiredRoles && requiredRoles.length > 0 && !hasAnyRole(requiredRoles)) {
    next('/403')
    return
  }
  
  next()
})

export default router
