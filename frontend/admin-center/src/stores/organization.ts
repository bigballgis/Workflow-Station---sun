import { defineStore } from 'pinia'
import { ref } from 'vue'
import { organizationApi, Department } from '@/api/organization'

export const useOrganizationStore = defineStore('organization', () => {
  const departmentTree = ref<Department[]>([])
  const loading = ref(false)
  const currentDepartment = ref<Department | null>(null)

  const fetchTree = async () => {
    loading.value = true
    try {
      departmentTree.value = await organizationApi.getTree()
    } finally {
      loading.value = false
    }
  }

  const createDepartment = async (data: any) => {
    await organizationApi.create(data)
    await fetchTree()
  }

  const updateDepartment = async (id: string, data: any) => {
    await organizationApi.update(id, data)
    await fetchTree()
  }

  const deleteDepartment = async (id: string) => {
    await organizationApi.delete(id)
    await fetchTree()
  }

  const moveDepartment = async (id: string, data: any) => {
    await organizationApi.move(id, data)
    await fetchTree()
  }

  return { departmentTree, loading, currentDepartment, fetchTree, createDepartment, updateDepartment, deleteDepartment, moveDepartment }
})
