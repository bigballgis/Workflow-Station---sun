import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardApi, type DashboardOverview, type DashboardWidget } from '@/api/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  const overview = ref<DashboardOverview | null>(null)
  const widgets = ref<DashboardWidget[]>([])
  const loading = ref(false)

  // Concurrent-call dedupe: multiple dashboard widgets call fetchOverview() on
  // mount; without this each would fire its own identical request. Reuse the
  // in-flight promise so the endpoint is hit once per dashboard load.
  let overviewInFlight: Promise<void> | null = null

  const fetchOverview = (): Promise<void> => {
    if (overviewInFlight) return overviewInFlight
    loading.value = true
    overviewInFlight = (async () => {
      try {
        const res = await dashboardApi.getOverview()
        overview.value = res.data
      } finally {
        loading.value = false
        overviewInFlight = null
      }
    })()
    return overviewInFlight
  }

  const fetchWidgets = async () => {
    const res = await dashboardApi.getWidgets()
    widgets.value = res.data
  }

  const saveLayout = async (layout: DashboardWidget[]) => {
    await dashboardApi.saveLayout(layout)
    widgets.value = layout
  }

  const resetLayout = async () => {
    await dashboardApi.resetLayout()
    await fetchWidgets()
  }

  return {
    overview,
    widgets,
    loading,
    fetchOverview,
    fetchWidgets,
    saveLayout,
    resetLayout
  }
})
