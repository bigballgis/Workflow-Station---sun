import { defineStore } from 'pinia'
import { ref } from 'vue'
import { biManagementApi, type DashboardAssignmentResponse, type AssignmentListParams } from '@/api/biManagement'

export const useBiManagementStore = defineStore('biManagement', () => {
  const assignments = ref<DashboardAssignmentResponse[]>([])
  const total = ref(0)
  const loading = ref(false)

  const fetchAssignments = async (params?: AssignmentListParams) => {
    loading.value = true
    try {
      const res = await biManagementApi.assignment.list(params)
      assignments.value = res.content
      total.value = res.totalElements
    } finally {
      loading.value = false
    }
  }

  const deleteAssignment = async (id: string) => {
    await biManagementApi.assignment.delete(id)
  }

  return { assignments, total, loading, fetchAssignments, deleteAssignment }
})
