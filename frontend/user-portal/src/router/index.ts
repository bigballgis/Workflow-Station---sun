import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import {
  applyWorkspaceAwarePortalAccess,
  getCurrentUser,
  getStoredUser,
  listWorkspaceContexts,
  reconcilePortalWorkspaceSession,
  saveUser,
  type UserInfo
} from '@/api/auth'
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
    component: () => import('@/views/login/index.vue'),
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
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const t = i18n.global.t
  const titleKey = to.meta.titleKey as string
  const pageTitle = titleKey ? t(titleKey) : t('app.name')
  document.title = `${pageTitle} - ${t('app.title')}`
  
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token && to.path !== '/login') {
    next('/login')
    return
  }

  // 补录 UBR 后须换发 JWT（reconcile），否则仅改 localStorage 仍被 PortalSelfServiceAccessFilter 拦截
  if (token && to.path !== '/login' && to.path !== '/403') {
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
        localStorage.setItem('userId', merged.userId)
      } catch {
        // 保持缓存
      }
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
  if (user?.portalAccessMode === 'PERMISSION_SELF_SERVICE_ONLY' && to.path !== '/login' && to.path !== '/403') {
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
