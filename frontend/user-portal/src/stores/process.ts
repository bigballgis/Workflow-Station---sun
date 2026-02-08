import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/api/request'

export interface ProcessDefinition {
  id: string
  key: string
  name: string
  description?: string
  category: string
  version: string
  icon?: string
  isFavorite?: boolean
}

export interface ProcessInstance {
  id: string
  processDefinitionId: string
  processDefinitionName: string
  businessKey?: string
  startTime: string
  endTime?: string
  status: string
  startUserId: string
  startUserName: string
}

export const useProcessStore = defineStore('process', () => {
  const definitions = ref<ProcessDefinition[]>([])
  const myApplications = ref<ProcessInstance[]>([])
  const currentProcess = ref<ProcessInstance | null>(null)
  const loading = ref(false)
  const total = ref(0)

  const fetchDefinitions = async (params?: { category?: string; keyword?: string }) => {
    loading.value = true
    try {
      const res = await request.get('/api/processes/definitions', { params })
      definitions.value = res.data
    } finally {
      loading.value = false
    }
  }

  const fetchMyApplications = async (params: { page: number; size: number; status?: string }) => {
    loading.value = true
    try {
      const res = await request.get('/api/processes/my-applications', { params })
      myApplications.value = res.data.content
      total.value = res.data.totalElements
    } finally {
      loading.value = false
    }
  }

  const startProcess = async (processKey: string, data: { variables?: Record<string, any>; businessKey?: string }) => {
    const res = await request.post(`/api/processes/${processKey}/start`, data)
    return res.data
  }

  const getProcessDetail = async (processId: string) => {
    const res = await request.get(`/api/processes/${processId}`)
    currentProcess.value = res.data
    return res.data
  }

  const withdrawProcess = async (processId: string, reason: string) => {
    await request.post(`/api/processes/${processId}/withdraw`, { reason })
  }

  const urgeProcess = async (processId: string) => {
    await request.post(`/api/processes/${processId}/urge`)
  }

  const toggleFavorite = async (processKey: string) => {
    await request.post(`/api/processes/${processKey}/favorite`)
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
