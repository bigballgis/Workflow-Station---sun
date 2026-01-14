import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { canAccessRoute, PERMISSIONS } from '@/utils/permission'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'

// Extend route meta type
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    titleKey?: string
    icon?: string
    hidden?: boolean
    requiresAuth?: boolean
    permissions?: string[]
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { titleKey: 'login.title', hidden: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { titleKey: 'menu.dashboard', icon: 'Odometer', permissions: [] }
      },
      {
        path: 'user',
        name: 'UserManagement',
        meta: { titleKey: 'menu.userManagement', icon: 'User', permissions: [PERMISSIONS.USER_READ] },
        children: [
          {
            path: 'list',
            name: 'UserList',
            component: () => import('@/views/user/UserList.vue'),
            meta: { titleKey: 'menu.userList', permissions: [PERMISSIONS.USER_READ] }
          },
          {
            path: 'import',
            name: 'UserImport',
            component: () => import('@/views/user/UserImport.vue'),
            meta: { titleKey: 'menu.userImport', permissions: [PERMISSIONS.USER_WRITE] }
          }
        ]
      },
      {
        path: 'organization',
        name: 'Organization',
        component: () => import('@/views/organization/DepartmentTree.vue'),
        meta: { titleKey: 'menu.organization', icon: 'OfficeBuilding', permissions: [PERMISSIONS.USER_READ] }
      },
      {
        path: 'organization/department',
        redirect: '/organization'
      },
      {
        path: 'virtual-group',
        name: 'VirtualGroup',
        component: () => import('@/views/virtual-group/index.vue'),
        meta: { titleKey: 'menu.virtualGroup', icon: 'Connection', permissions: [PERMISSIONS.USER_READ] }
      },
      {
        path: 'role',
        name: 'RoleManagement',
        component: () => import('@/views/role/RoleList.vue'),
        meta: { titleKey: 'menu.roleManagement', icon: 'Key', permissions: [PERMISSIONS.ROLE_READ] }
      },
      {
        path: 'role/list',
        redirect: '/role'
      },
      {
        path: 'function-unit',
        name: 'FunctionUnit',
        component: () => import('@/views/function-unit/index.vue'),
        meta: { titleKey: 'menu.functionUnit', icon: 'Box', permissions: [PERMISSIONS.SYSTEM_ADMIN] }
      },
      {
        path: 'dictionary',
        name: 'Dictionary',
        component: () => import('@/views/dictionary/index.vue'),
        meta: { titleKey: 'menu.dictionary', icon: 'Collection', permissions: [PERMISSIONS.SYSTEM_ADMIN] }
      },
      {
        path: 'monitor',
        name: 'SystemMonitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { titleKey: 'menu.monitor', icon: 'Monitor', permissions: [PERMISSIONS.SYSTEM_ADMIN] }
      },
      {
        path: 'audit',
        name: 'AuditLog',
        component: () => import('@/views/audit/index.vue'),
        meta: { titleKey: 'menu.audit', icon: 'Document', permissions: [PERMISSIONS.AUDIT_READ] }
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/config/index.vue'),
        meta: { titleKey: 'menu.config', icon: 'Setting', permissions: [PERMISSIONS.SYSTEM_ADMIN] }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { titleKey: 'profile.title', icon: 'User', hidden: true, permissions: [] }
      },
      {
        path: '403',
        name: 'Forbidden',
        component: () => import('@/views/error/403.vue'),
        meta: { title: '无权限', hidden: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const t = i18n.global.t
  const pageTitle = to.meta.titleKey ? t(to.meta.titleKey) : (to.meta.title || t('app.name'))
  document.title = `${pageTitle} - ${t('app.title')}`
  
  // Check login status
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }
  
  // Skip permission check for login page
  if (to.path === '/login') {
    next()
    return
  }
  
  // Check route permissions
  if (!canAccessRoute(to.path)) {
    ElMessage.warning('您没有权限访问该页面')
    next('/403')
    return
  }
  
  next()
})

export default router
