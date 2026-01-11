import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { canAccessRoute, PERMISSIONS } from '@/utils/permission'
import { ElMessage } from 'element-plus'

// Extend route meta type
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
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
    meta: { title: '登录', hidden: true }
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
        meta: { title: '仪表盘', icon: 'Odometer', permissions: [] }
      },
      {
        path: 'user',
        name: 'UserManagement',
        meta: { title: '用户管理', icon: 'User', permissions: [PERMISSIONS.USER_READ] },
        children: [
          {
            path: 'list',
            name: 'UserList',
            component: () => import('@/views/user/UserList.vue'),
            meta: { title: '用户列表', permissions: [PERMISSIONS.USER_READ] }
          },
          {
            path: 'import',
            name: 'UserImport',
            component: () => import('@/views/user/UserImport.vue'),
            meta: { title: '批量导入', permissions: [PERMISSIONS.USER_WRITE] }
          }
        ]
      },
      {
        path: 'organization',
        name: 'Organization',
        component: () => import('@/views/organization/DepartmentTree.vue'),
        meta: { title: '组织架构', icon: 'OfficeBuilding', permissions: [PERMISSIONS.USER_READ] }
      },
      {
        path: 'organization/department',
        redirect: '/organization'
      },
      {
        path: 'virtual-group',
        name: 'VirtualGroup',
        component: () => import('@/views/virtual-group/index.vue'),
        meta: { title: '虚拟组管理', icon: 'Connection', permissions: [PERMISSIONS.USER_READ] }
      },
      {
        path: 'role',
        name: 'RoleManagement',
        component: () => import('@/views/role/RoleList.vue'),
        meta: { title: '角色管理', icon: 'Key', permissions: [PERMISSIONS.ROLE_READ] }
      },
      {
        path: 'role/list',
        redirect: '/role'
      },
      {
        path: 'function-unit',
        name: 'FunctionUnit',
        component: () => import('@/views/function-unit/index.vue'),
        meta: { title: '功能单元', icon: 'Box', permissions: [PERMISSIONS.SYSTEM_ADMIN] }
      },
      {
        path: 'dictionary',
        name: 'Dictionary',
        component: () => import('@/views/dictionary/index.vue'),
        meta: { title: '数据字典', icon: 'Collection', permissions: [PERMISSIONS.SYSTEM_ADMIN] }
      },
      {
        path: 'monitor',
        name: 'SystemMonitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { title: '系统监控', icon: 'Monitor', permissions: [PERMISSIONS.SYSTEM_ADMIN] }
      },
      {
        path: 'audit',
        name: 'AuditLog',
        component: () => import('@/views/audit/index.vue'),
        meta: { title: '审计日志', icon: 'Document', permissions: [PERMISSIONS.AUDIT_READ] }
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/config/index.vue'),
        meta: { title: '系统配置', icon: 'Setting', permissions: [PERMISSIONS.SYSTEM_ADMIN] }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User', hidden: true, permissions: [] }
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
  document.title = `${to.meta.title || '管理员中心'} - Admin Center`
  
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
