import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/apis' },
  { path: '/apis', name: 'GatewayApis', component: () => import('@/pages/apis/index.vue') },
  { path: '/applications', name: 'GatewayApps', component: () => import('@/pages/applications/index.vue') },
  { path: '/releases', name: 'GatewayReleases', component: () => import('@/pages/releases/index.vue') },
  { path: '/audit', name: 'GatewayAudit', component: () => import('@/pages/audit/index.vue') },
  { path: '/drift', name: 'GatewayDrift', component: () => import('@/pages/drift/index.vue') },
  { path: '/monitoring', name: 'GatewayMonitoring', component: () => import('@/pages/monitoring/index.vue') },
  { path: '/catalog', name: 'GatewayCatalog', component: () => import('@/pages/catalog/index.vue') },
  { path: '/subscriptions', name: 'GatewaySubs', component: () => import('@/pages/subscriptions/index.vue') },
  { path: '/rules', name: 'GatewayRules', component: () => import('@/pages/rules/index.vue') },
  { path: '/provider', name: 'GatewayProvider', component: () => import('@/pages/provider/index.vue') },
  { path: '/compliance', name: 'GatewayCompliance', component: () => import('@/pages/compliance/index.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/apis' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})
export default router
