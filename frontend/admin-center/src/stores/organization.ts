import { defineStore } from 'pinia'
import { ref } from 'vue'
import { organizationApi, BusinessUnit } from '@/api/organization'

export const useOrganizationStore = defineStore('organization', () => {
  const businessUnitTree = ref<BusinessUnit[]>([])
  const loading = ref(false)
  const currentBusinessUnit = ref<BusinessUnit | null>(null)

  const fetchTree = async () => {
    loading.value = true
    try {
      businessUnitTree.value = await organizationApi.getTree()
    } finally {
      loading.value = false
    }
  }

  const createBusinessUnit = async (data: any) => {
    await organizationApi.create(data)
    await fetchTree()
  }

  const updateBusinessUnit = async (id: string, data: any) => {
    await organizationApi.update(id, data)
    await fetchTree()
  }

  const deleteBusinessUnit = async (id: string) => {
    await organizationApi.delete(id)
    await fetchTree()
  }

  const moveBusinessUnit = async (id: string, data: any) => {
    await organizationApi.move(id, data)
    await fetchTree()
  }

  return { 
    businessUnitTree, 
    loading, 
    currentBusinessUnit, 
    fetchTree, 
    createBusinessUnit, 
    updateBusinessUnit, 
    deleteBusinessUnit, 
    moveBusinessUnit
  }
})
