import { createPinia } from 'pinia'
import type { Router } from 'vue-router'

/**
 * Host Runtime Ownership
 *
 * Single source of truth for all MFE runtime dependencies.
 * The host creates these instances once. RemoteLoader imports them
 * and passes them to remote Vue apps so all MFEs share the same
 * Pinia instance as the host — no duplicated runtimes, no setActivePinia hacks.
 *
 * Usage in federation mode:
 *   RemoteLoader: import { pinia } from '@/runtime' → app.use(pinia)
 *   MFE remote:   defineStore('x', ...) → useXStore()  ← same pinia instance
 *
 * Standalone dev mode (MFE main.ts):
 *   app.use(createPinia())  ← isolated; does NOT use host runtime
 */
export const pinia = createPinia()

/** Set by main.ts after router creation */
export let router: Router

export function setHostRouter(r: Router) {
  router = r
}
