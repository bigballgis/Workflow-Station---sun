import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { BASIC_HASH_REDIRECTS } from './formCtlBasic'
import { EXTEND_HASH_REDIRECTS } from './formCtlExtend'
import { GUIDELINES } from './guidelines'
import i18n from './i18n'
import { helpHistoryEntryKey, installHelpScroll } from './utils/helpScroll'

const helpScroll = installHelpScroll()

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HelpHome.vue'),
    meta: { titleKey: 'home.title' },
  },
  ...GUIDELINES.map((guide) => ({
    path: guide.path,
    name: guide.id,
    component: guide.load,
    meta: { titleKey: guide.titleKey },
  })),
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  // Articles scroll in `main.help-main`, not the window: helpScroll does the scrolling.
  scrollBehavior(to) {
    void helpScroll.navigate(helpHistoryEntryKey(to.fullPath), to.hash)
    return false
  },
})

router.afterEach((to) => {
  const key = typeof to.meta.titleKey === 'string' ? to.meta.titleKey : 'app.name'
  const title = i18n.global.t(key)
  document.title = `${title} · ${i18n.global.t('app.name')}`
})

router.beforeEach((to) => {
  const hash = to.hash.replace(/^#/, '')
  if (to.path === '/form-events-basic') {
    const dest = hash ? BASIC_HASH_REDIRECTS[hash] : undefined
    if (dest) return dest
  }
  if (to.path === '/form-events-extend') {
    const dest = hash ? EXTEND_HASH_REDIRECTS[hash] : undefined
    if (dest) return dest
  }
})

export default router
