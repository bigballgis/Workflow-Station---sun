import { defineStore } from 'pinia'
import { ref } from 'vue'
import { functionUnitApi, type FunctionUnit, type Deployment } from '@/api/functionUnit'
import { deduplicateByCode } from '@/utils/version'

export const useFunctionUnitStore = defineStore('functionUnit', () => {
  const functionUnits = ref<FunctionUnit[]>([])
  const archivedFunctionUnits = ref<FunctionUnit[]>([])
  const deployments = ref<Deployment[]>([])
  const loading = ref(false)
  const archivedLoading = ref(false)
  const deploymentsLoading = ref(false)

  const fetchFunctionUnits = async () => {
    loading.value = true
    try {
      const pageSize = 100
      const raw: FunctionUnit[] = []
      let page = 0
      let totalPages = 1
      let guard = 0
      while (page < totalPages && guard++ < 500) {
        const result = await functionUnitApi.list(undefined, page, pageSize)
        raw.push(...result.content)
        totalPages = result.totalPages
        page++
      }
      functionUnits.value = deduplicateByCode(raw)
    } finally {
      loading.value = false
    }
  }

  const fetchArchivedFunctionUnits = async () => {
    archivedLoading.value = true
    try {
      const pageSize = 100
      const raw: FunctionUnit[] = []
      let page = 0
      let totalPages = 1
      let guard = 0
      while (page < totalPages && guard++ < 500) {
        const result = await functionUnitApi.listArchived(page, pageSize)
        raw.push(...result.content)
        totalPages = result.totalPages
        page++
      }
      archivedFunctionUnits.value = deduplicateByCode(raw)
    } finally {
      archivedLoading.value = false
    }
  }

  const fetchDeployments = async () => {
    deploymentsLoading.value = true
    try {
      const pageSize = 100
      const raw: Deployment[] = []
      let page = 0
      let totalPages = 1
      let guard = 0
      while (page < totalPages && guard++ < 500) {
        const result = await functionUnitApi.getAllDeployments(page, pageSize)
        raw.push(...result.content)
        totalPages = result.totalPages
        page++
      }
      deployments.value = raw
    } finally {
      deploymentsLoading.value = false
    }
  }

  const setEnabled = async (id: string, enabled: boolean) => {
    await functionUnitApi.setEnabled(id, enabled)
    const unit = functionUnits.value.find(u => u.id === id)
    if (unit) unit.enabled = enabled
  }

  const deleteFunctionUnit = async (id: string) => {
    await functionUnitApi.delete(id)
    functionUnits.value = functionUnits.value.filter(u => u.id !== id)
  }

  const batchSetEnabled = async (ids: string[], enabled: boolean) => {
    await functionUnitApi.batchSetEnabled(ids, enabled)
    for (const unit of functionUnits.value) {
      if (ids.includes(unit.id)) unit.enabled = enabled
    }
  }

  const batchDelete = async (ids: string[]) => {
    await functionUnitApi.batchDelete(ids)
    functionUnits.value = functionUnits.value.filter(u => !ids.includes(u.id))
  }

  return {
    functionUnits, archivedFunctionUnits, deployments, loading, archivedLoading, deploymentsLoading,
    fetchFunctionUnits, fetchArchivedFunctionUnits, fetchDeployments,
    setEnabled, deleteFunctionUnit, batchSetEnabled, batchDelete,
  }
})
