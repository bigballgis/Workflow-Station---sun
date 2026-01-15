import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import i18n from '@/i18n'

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
        name: 'MyRequests',
        component: () => import('@/views/permissions/my-requests.vue'),
        meta: { titleKey: 'menu.myRequests', icon: 'Document' }
      },
      {
        path: 'approvals',
        name: 'Approvals',
        component: () => import('@/views/permissions/approvals.vue'),
        meta: { titleKey: 'menu.approvals', icon: 'Checked' }
      },
      {
        path: 'member-management',
        name: 'MemberManagement',
        component: () => import('@/views/permissions/member-management.vue'),
        meta: { titleKey: 'menu.memberManagement', icon: 'UserFilled' }
      },
      {
        path: 'exit-role',
        name: 'ExitRole',
        component: () => import('@/views/permissions/exit-role.vue'),
        meta: { titleKey: 'menu.exitRole', icon: 'SwitchButton' }
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/notifications/index.vue'),
        meta: { titleKey: 'menu.notifications', icon: 'Bell' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/index.vue'),
        meta: { titleKey: 'menu.settings', icon: 'Setting' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { titleKey: 'profile.title', icon: 'User', hidden: true }
      }
    ]
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
router.beforeEach((to, from, next) => {
  // 设置页面标题
  const t = i18n.global.t
  const titleKey = to.meta.titleKey as string
  const pageTitle = titleKey ? t(titleKey) : t('app.name')
  document.title = `${pageTitle} - ${t('app.title')}`
  
  // 检查登录状态
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token && to.path !== '/login') {
    next('/login')
  } else {
    next()
  }
})

export default router
