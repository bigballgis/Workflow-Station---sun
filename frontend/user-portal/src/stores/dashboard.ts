import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardApi, type DashboardOverview, type DashboardWidget } from '@/api/dashboard'
import { getDashboardOverview } from '@/api/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  const overview = ref<DashboardOverview | null>(null)
  const widgets = ref<DashboardWidget[]>([])
  const loading = ref(false)

  const fetchOverview = async () => {
    loading.value = true
    try {
      const res = await getDashboardOverview()
      overview.value = res.data
    } finally {
      loading.value = false
    }
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
