import { defineStore } from 'pinia'
import { ref } from 'vue'
import { relationTableStructureApi, type RelationTableResponse } from '@/api/relationTable'

export const useRelationTableStore = defineStore('relationTable', () => {
  const tableList = ref<RelationTableResponse[]>([])
  const loading = ref(false)

  const fetchTableList = async () => {
    loading.value = true
    try {
      tableList.value = await relationTableStructureApi.list()
    } finally {
      loading.value = false
    }
  }

  const setEnabled = async (id: number, enabled: boolean) => {
    await relationTableStructureApi.setEnabled(id, enabled)
    const idx = tableList.value.findIndex((t) => t.id === id)
    if (idx !== -1) {
      tableList.value[idx] = { ...tableList.value[idx], enabled }
    }
  }

  const setPortalVisibility = async (id: number, visible: boolean) => {
    await relationTableStructureApi.setPortalVisibility(id, visible)
    const idx = tableList.value.findIndex((t) => t.id === id)
    if (idx !== -1) {
      tableList.value[idx] = { ...tableList.value[idx], portalVisible: visible }
    }
  }

  const deployTable = async (id: number) => {
    const updated = await relationTableStructureApi.deploy(id)
    const idx = tableList.value.findIndex((t) => t.id === id)
    if (idx !== -1) {
      tableList.value[idx] = updated
    }
  }

  const deleteTable = async (id: number) => {
    await relationTableStructureApi.delete(id)
    tableList.value = tableList.value.filter((t) => t.id !== id)
  }

  return { tableList, loading, fetchTableList, setEnabled, setPortalVisibility, deployTable, deleteTable }
})
