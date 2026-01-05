import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/function-units',
    children: [
      {
        path: 'function-units',
        name: 'FunctionUnits',
        component: () => import('@/views/function-unit/FunctionUnitList.vue'),
        meta: { title: '功能单元' }
      },
      {
        path: 'function-units/:id',
        name: 'FunctionUnitEdit',
        component: () => import('@/views/function-unit/FunctionUnitEdit.vue'),
        meta: { title: '编辑功能单元' }
      },
      {
        path: 'icons',
        name: 'IconLibrary',
        component: () => import('@/views/icon/IconLibrary.vue'),
        meta: { title: '图标库' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
