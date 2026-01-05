import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

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
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      },
      {
        path: 'user',
        name: 'UserManagement',
        meta: { title: '用户管理', icon: 'User' },
        children: [
          {
            path: 'list',
            name: 'UserList',
            component: () => import('@/views/user/UserList.vue'),
            meta: { title: '用户列表' }
          },
          {
            path: 'import',
            name: 'UserImport',
            component: () => import('@/views/user/UserImport.vue'),
            meta: { title: '批量导入' }
          }
        ]
      },
      {
        path: 'organization',
        name: 'Organization',
        meta: { title: '组织架构', icon: 'OfficeBuilding' },
        children: [
          {
            path: 'department',
            name: 'DepartmentTree',
            component: () => import('@/views/organization/DepartmentTree.vue'),
            meta: { title: '部门管理' }
          }
        ]
      },
      {
        path: 'role',
        name: 'RoleManagement',
        meta: { title: '角色权限', icon: 'Key' },
        children: [
          {
            path: 'list',
            name: 'RoleList',
            component: () => import('@/views/role/RoleList.vue'),
            meta: { title: '角色列表' }
          },
          {
            path: 'permission',
            name: 'PermissionConfig',
            component: () => import('@/views/role/PermissionConfig.vue'),
            meta: { title: '权限配置' }
          }
        ]
      },
      {
        path: 'virtual-group',
        name: 'VirtualGroup',
        component: () => import('@/views/virtual-group/index.vue'),
        meta: { title: '虚拟组管理', icon: 'Connection' }
      },
      {
        path: 'function-unit',
        name: 'FunctionUnit',
        component: () => import('@/views/function-unit/index.vue'),
        meta: { title: '功能单元', icon: 'Box' }
      },
      {
        path: 'dictionary',
        name: 'Dictionary',
        component: () => import('@/views/dictionary/index.vue'),
        meta: { title: '数据字典', icon: 'Collection' }
      },
      {
        path: 'monitor',
        name: 'SystemMonitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { title: '系统监控', icon: 'Monitor' }
      },
      {
        path: 'audit',
        name: 'AuditLog',
        component: () => import('@/views/audit/index.vue'),
        meta: { title: '审计日志', icon: 'Document' }
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/config/index.vue'),
        meta: { title: '系统配置', icon: 'Setting' }
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
  next()
})

export default router
