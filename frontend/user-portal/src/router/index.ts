import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import {
  clearAuth,
  getStoredUser,
  type UserInfo,
  verifyPortalSession
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
        name: 'Tasks',
        component: () => import('@/views/tasks/index.vue'),
        meta: { titleKey: 'menu.tasks', icon: 'List' }
      },
      {
        path: 'tasks/completed',
        name: 'CompletedTasks',
        component: () => import('@/views/tasks/completed.vue'),
        meta: { titleKey: 'menu.completedTasks', icon: 'Finished' }
      },
      {
        path: 'tasks/:id',
        name: 'TaskDetail',
        component: () => import('@/views/tasks/detail.vue'),
        meta: { titleKey: 'task.detail', hidden: true }
      },
      {
        path: 'processes',
        name: 'Processes',
        component: () => import('@/views/processes/index.vue'),
        meta: { titleKey: 'menu.processes', icon: 'Plus' }
      },
      {
        path: 'processes/start/:key',
        name: 'ProcessStart',
        component: () => import('@/views/processes/start.vue'),
        meta: { titleKey: 'process.startProcess', hidden: true }
      },
      {
        path: 'my-applications',
        name: 'MyApplications',
        component: () => import('@/views/applications/index.vue'),
        meta: { titleKey: 'menu.myApplications', icon: 'Document' }
      },
      {
        path: 'applications/:id',
        name: 'ApplicationDetail',
        component: () => import('@/views/applications/detail.vue'),
        meta: { titleKey: 'application.title', hidden: true }
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
      },
      {
        path: 'views/:functionUnitCode?',
        name: 'MainTableViews',
        component: () => import('@/views/main-table-views/index.vue'),
        meta: { titleKey: 'menu.views', icon: 'View' }
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
let verifiedPortalUser: UserInfo | null = null
let verifyingPortalSession: Promise<UserInfo> | null = null
async function getVerifiedPortalUser(): Promise<UserInfo> {
  if (verifiedPortalUser) return verifiedPortalUser
  if (!verifyingPortalSession) {
    verifyingPortalSession = verifyPortalSession()
      .then((user) => {
        verifiedPortalUser = user
        return user
      })
      .finally(() => {
        verifyingPortalSession = null
      })
  }
  return verifyingPortalSession
}
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
  // Auth is verified by /me because the real session is an httpOnly cookie.
  let verifiedUser: UserInfo | null = null
  if (to.meta.requiresAuth !== false && to.path !== '/sso/callback') {
    try {
      const sessionUser = await getVerifiedPortalUser()
      verifiedUser = getStoredUser() || sessionUser
    } catch {
      verifiedPortalUser = null
      clearAuth()
      setSsoReturnPath(to.fullPath)
      redirectToUnifiedLogin('portal')
      return next(false)
    }
  }
  const requiredRoles = to.meta.requiredRoles
  if (requiredRoles && requiredRoles.length > 0) {
    const user = verifiedUser || getStoredUser()
    if (!user?.roles || !requiredRoles.some(role => user.roles.includes(role))) {
      next('/403')
      return
    }
  }
  const user: UserInfo | null = verifiedUser || getStoredUser()
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
  next()
})
export default router