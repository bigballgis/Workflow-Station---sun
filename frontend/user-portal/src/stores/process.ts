import { defineStore } from 'pinia'
import { ref } from 'vue'
import { processApi, type ProcessDefinition, type ProcessInstance } from '@/api/process'

export const useProcessStore = defineStore('process', () => {
  const definitions = ref<ProcessDefinition[]>([])
  const myApplications = ref<ProcessInstance[]>([])
  const currentProcess = ref<ProcessInstance | null>(null)
  const loading = ref(false)
  const total = ref(0)

  const fetchDefinitions = async (params?: { category?: string; keyword?: string }) => {
    loading.value = true
    try {
      const res = await processApi.getDefinitions(params)
      definitions.value = res.data
    } finally {
      loading.value = false
    }
  }

  const fetchMyApplications = async (params: { page: number; size: number; status?: string }) => {
    loading.value = true
    try {
      const res = await processApi.getMyApplications(params)
      myApplications.value = res.data.content
      total.value = res.data.totalElements
    } finally {
      loading.value = false
    }
  }

  const startProcess = async (processKey: string, data: { variables?: Record<string, any>; businessKey?: string }) => {
    const res = await processApi.startProcess(processKey, data)
    return res.data
  }

  const getProcessDetail = async (processId: string) => {
    const res = await processApi.getProcessDetail(processId)
    currentProcess.value = res.data
    return res.data
  }

  const withdrawProcess = async (processId: string, reason: string) => {
    await processApi.withdrawProcess(processId, reason)
  }

  const urgeProcess = async (processId: string) => {
    await processApi.urgeProcess(processId)
  }

  const toggleFavorite = async (processKey: string) => {
    await processApi.toggleFavorite(processKey)
  }

  return {
    definitions,
    myApplications,
    currentProcess,
    loading,
    total,
    fetchDefinitions,
    fetchMyApplications,
    startProcess,
    getProcessDetail,
    withdrawProcess,
    urgeProcess,
    toggleFavorite
  }
})
