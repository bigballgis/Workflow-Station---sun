import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/PortalLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'HomeFilled' }
      },
      {
        path: 'tasks',
        name: 'Tasks',
        component: () => import('@/views/tasks/index.vue'),
        meta: { title: '待办任务', icon: 'List' }
      },
      {
        path: 'tasks/:id',
        name: 'TaskDetail',
        component: () => import('@/views/tasks/detail.vue'),
        meta: { title: '任务详情', hidden: true }
      },
      {
        path: 'processes',
        name: 'Processes',
        component: () => import('@/views/processes/index.vue'),
        meta: { title: '发起流程', icon: 'Plus' }
      },
      {
        path: 'processes/start/:key',
        name: 'ProcessStart',
        component: () => import('@/views/processes/start.vue'),
        meta: { title: '发起流程', hidden: true }
      },
      {
        path: 'my-applications',
        name: 'MyApplications',
        component: () => import('@/views/applications/index.vue'),
        meta: { title: '我的申请', icon: 'Document' }
      },
      {
        path: 'applications/:id',
        name: 'ApplicationDetail',
        component: () => import('@/views/applications/detail.vue'),
        meta: { title: '申请详情', hidden: true }
      },
      {
        path: 'delegations',
        name: 'Delegations',
        component: () => import('@/views/delegations/index.vue'),
        meta: { title: '委托管理', icon: 'Share' }
      },
      {
        path: 'permissions',
        name: 'Permissions',
        component: () => import('@/views/permissions/index.vue'),
        meta: { title: '权限申请', icon: 'Key' }
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/notifications/index.vue'),
        meta: { title: '消息中心', icon: 'Bell' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/index.vue'),
        meta: { title: '个人设置', icon: 'Setting' }
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
  document.title = `${to.meta.title || '用户门户'} - 工作流平台`
  
  // 检查登录状态
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token && to.path !== '/login') {
    next('/login')
  } else {
    next()
  }
})

export default router
