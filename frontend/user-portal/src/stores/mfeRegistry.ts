import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchMfeRuntimeConfig, type MfeModuleConfig } from '@/api/mfeRegistry'

/**
 * Pinia store for MFE runtime configuration.
 * Fetches from admin-center runtime API and caches.
 * Filters are applied locally: enabled=true (server pre-filters),
 * permission check, and tenant scope (done by host at runtime).
 */
export const useMfeRegistryStore = defineStore('mfeRegistry', () => {
  const modules = ref<MfeModuleConfig[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const loaded = ref(false)

  /** Modules sorted by orderNo, ready for nav rendering */
  const navModules = computed(() =>
    [...modules.value].sort((a, b) => a.orderNo - b.orderNo)
  )

  /** Load runtime config from admin-center */
  async function load(hostApp: string = 'user-portal', env: string = 'DEV') {
    if (loaded.value && modules.value.length > 0) return modules.value
    loading.value = true
    error.value = null
    try {
      modules.value = await fetchMfeRuntimeConfig(hostApp, env)
      loaded.value = true
    } catch (e: any) {
      error.value = e.message || 'Failed to load MFE config'
      console.error('[MFE] Failed to load runtime config:', e)
    } finally {
      loading.value = false
    }
    return modules.value
  }

  /** Get module by route path */
  function getByRoutePath(path: string): MfeModuleConfig | undefined {
    return modules.value.find(m => m.routePath === path)
  }

  /** Clear cache (for refresh or env switch) */
  function reset() {
    modules.value = []
    loaded.value = false
    error.value = null
  }

  return {
    modules,
    navModules,
    loading,
    error,
    loaded,
    load,
    getByRoutePath,
    reset
  }
})
