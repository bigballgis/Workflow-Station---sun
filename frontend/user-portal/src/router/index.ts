import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import {
  applyWorkspaceAwarePortalAccess,
  getCurrentUser,
  getStoredUser,
  listWorkspaceContexts,
  reconcilePortalWorkspaceSession,
  saveUser,
  TOKEN_KEY,
  USER_ID_KEY,
  type UserInfo
} from '@/api/auth'
import i18n from '@/i18n'
import { redirectToUnifiedLogin, setSsoReturnPath } from '@/utils/sso'

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
    component: () => import('@/views/sso/SsoCallback.vue'),
    meta: { titleKey: 'login.title', requiresAuth: false }
  },
  {
    path: '/login',
    name: 'UnifiedLogin',
    component: () => import('@/views/login/UnifiedLogin.vue'),
    meta: { titleKey: 'login.title', requiresAuth: false }
  },
  {
    path: '/',
    name: 'PortalRoot',
    component: () => import('@/layouts/PortalLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { titleKey: 'menu.dashboard', icon: 'HomeFilled' }
      },
      {
        path: 'tasks',
        redirect: '/mfe/workflow#/tasks'
      },
      {
        path: 'tasks/completed',
        redirect: '/mfe/workflow#/tasks/completed'
      },
      {
        path: 'tasks/:id',
        redirect: (to: any) => `/mfe/workflow#/tasks/detail?id=${to.params.id}`
      },
      {
        path: 'processes',
        redirect: '/mfe/workflow#/processes'
      },
      {
        path: 'processes/start/:key',
        redirect: (to: any) => `/mfe/workflow#/processes/start?key=${to.params.key}`
      },
      {
        path: 'my-applications',
        redirect: '/mfe/workflow#/applications'
      },
      {
        path: 'applications/:id',
        redirect: (to: any) => `/mfe/workflow#/applications/detail?id=${to.params.id}`
      },
      {
        path: 'delegations',
        name: 'Delegations',
        component: () => import('@/views/delegations/index.vue'),
        meta: { titleKey: 'menu.delegations', icon: 'Share' }
      },
      {
        path: 'permissions',
        name: 'Permissions',
        component: () => import('@/views/permissions/index.vue'),
        meta: { titleKey: 'menu.permissions', icon: 'Key' }
      },
      {
        path: 'gateway/approvals',
        name: 'GatewayApprovals',
        component: () => import('@/views/gateway/approvals.vue'),
        meta: { titleKey: 'gateway.subscriptionApprovals', icon: 'Connection' }
      },
      {
        path: 'my-requests',
        redirect: { path: '/permissions' }
      },
      {
        path: 'approvals',
        redirect: { path: '/permissions' }
      },
      {
        path: 'member-management',
        name: 'MemberManagement',
        component: () => import('@/views/permissions/member-management.vue'),
        meta: { titleKey: 'menu.memberManagement', icon: 'UserFilled' }
      },
      {
        path: 'exit-role',
        redirect: { path: '/permissions' }
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/notifications/index.vue'),
        meta: { titleKey: 'menu.notifications', icon: 'Bell' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { titleKey: 'profile.title', icon: 'User', hidden: true }
      },
      {
        path: 'bi-dashboard',
        name: 'BiDashboard',
        component: () => import('@/views/landing/DashboardLanding.vue'),
        meta: { title: 'BI Dashboard', icon: 'DataAnalysis', requiresAuth: true }
      },
      {
        path: 'relation-tables',
        name: 'RelationTables',
        component: () => import('@/views/relation-tables/index.vue'),
        meta: { titleKey: 'menu.relationTables', icon: 'Grid' }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { titleKey: 'error.forbidden', requiresAuth: false }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue')
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const t = i18n.global.t
  const titleKey = to.meta.titleKey as string
  const pageTitle = titleKey ? t(titleKey) : t('app.name')
  document.title = `${pageTitle} - ${t('app.title')}`
  
  if (to.path === '/login') {
    const r = to.query.redirect
    if (import.meta.env.PROD) {
      setSsoReturnPath(typeof r === 'string' ? r : '/dashboard')
      redirectToUnifiedLogin('portal')
      return next(false)
    }
    if (typeof r === 'string') setSsoReturnPath(r)
    return next()
  }

  // Auth checked via httpOnly cookie — let API calls handle 401 redirect
  if (to.meta.requiresAuth !== false && to.path !== '/sso/callback') {
    try {
      await reconcilePortalWorkspaceSession()
      const cached = getStoredUser()
      if (cached?.portalAccessMode === 'PERMISSION_SELF_SERVICE_ONLY') {
        try {
          let contexts: Awaited<ReturnType<typeof listWorkspaceContexts>> = []
          try {
            contexts = await listWorkspaceContexts()
          } catch {
            contexts = []
          }
          const fresh = await getCurrentUser()
          const merged = applyWorkspaceAwarePortalAccess(fresh, contexts.length > 0)
          saveUser(merged)
          localStorage.setItem(USER_ID_KEY, merged.userId)
        } catch {
          // 保持缓存
        }
      }
    } catch {
      // Auth not available — continue without (API calls will handle 401)
    }
  }

  const requiredRoles = to.meta.requiredRoles
  if (requiredRoles && requiredRoles.length > 0) {
    const user = getStoredUser()
    if (!user?.roles || !requiredRoles.some(role => user.roles.includes(role))) {
      next('/403')
      return
    }
  }

  const user: UserInfo | null = getStoredUser()
  if (user?.portalAccessMode === 'PERMISSION_SELF_SERVICE_ONLY' && to.path !== '/403') {
    const allowed = new Set([
      '/permissions',
      '/notifications',
      '/profile'
    ])
    const ok =
      allowed.has(to.path) ||
      to.path.startsWith('/notifications/')
    if (!ok) {
      next('/permissions')
      return
    }
  }


  // If navigating to a likely MFE route that doesn't exist yet,
  // redirect to dashboard first so PortalLayout mounts and registers MFE routes
  if (to.path.startsWith('/mfe/') && (!to.name || to.name === 'NotFound') && !to.query.mfe_redirect) {
    // Encode hash with encodeURIComponent so it survives query parameter parsing
    const hashPart = to.hash ? encodeURIComponent(to.hash) : ''
    const redirectPath = to.path + hashPart
    next({ path: '/dashboard', query: { mfe_redirect: redirectPath } })
    return
  }

  next()
})

export default router