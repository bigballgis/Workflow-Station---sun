import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

const TaskIndex = () => import('@/views/tasks/index.vue')
const TaskDetail = () => import('@/views/tasks/detail.vue')
const TaskCompleted = () => import('@/views/tasks/completed.vue')
const ProcessIndex = () => import('@/views/processes/index.vue')
const ProcessStart = () => import('@/views/processes/start.vue')
const ApplicationIndex = () => import('@/views/applications/index.vue')
const ApplicationDetail = () => import('@/views/applications/detail.vue')

const routes: RouteRecordRaw[] = [
  { path: '/', component: TaskIndex },
  { path: '/tasks', name: 'TaskIndex', component: TaskIndex },
  { path: '/tasks/detail', name: 'TaskDetail', component: TaskDetail },
  { path: '/tasks/completed', name: 'TaskCompleted', component: TaskCompleted },
  { path: '/processes', name: 'ProcessIndex', component: ProcessIndex },
  { path: '/processes/start', name: 'ProcessStart', component: ProcessStart },
  { path: '/applications', name: 'ApplicationIndex', component: ApplicationIndex },
  { path: '/applications/detail', name: 'ApplicationDetail', component: ApplicationDetail },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
