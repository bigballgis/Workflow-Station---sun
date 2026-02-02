import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import i18n from '@/i18n'
import { createAuthGuard } from '@/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { titleKey: 'login.title' }
  },
  {
    path: '/no-permission',
    name: 'NoPermission',
    component: () => import('@/views/NoPermission.vue'),
    meta: { titleKey: 'error.noPermission' }
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
        meta: { titleKey: 'functionUnit.title' }
      },
      {
        path: 'function-units/:id',
        name: 'FunctionUnitEdit',
        component: () => import('@/views/function-unit/FunctionUnitEdit.vue'),
        meta: { titleKey: 'functionUnit.edit' }
      },
      {
        path: 'icons',
        name: 'IconLibrary',
        component: () => import('@/views/icon/IconLibrary.vue'),
        meta: { titleKey: 'icon.title' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { titleKey: 'profile.title', hidden: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const authGuard = createAuthGuard()

router.beforeEach(async (to, _from, next) => {
  const t = i18n.global.t
  const pageTitle = (to.meta as { titleKey?: string })?.titleKey
    ? t((to.meta as { titleKey: string }).titleKey)
    : ((to.meta as { title?: string })?.title || t('app.name'))
  document.title = `${pageTitle} - ${t('app.title')}`

  const redirect = await authGuard(to)
  if (redirect) {
    next(redirect)
    return
  }

  next()
})

export default router
