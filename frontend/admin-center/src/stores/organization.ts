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
    } catch (error: any) {
      // 如果是权限错误，显示友好提示
      if (error?.code === 403 || error?.code === '403' || error?.code === 'PERMISSION_DENIED') {
        console.error('权限不足，无法访问组织架构:', error)
        businessUnitTree.value = []
      } else {
        console.error('获取组织架构失败:', error)
        businessUnitTree.value = []
      }
      throw error
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
