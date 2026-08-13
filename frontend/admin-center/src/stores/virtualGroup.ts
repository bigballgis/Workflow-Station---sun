import { defineStore } from 'pinia'
import { ref } from 'vue'
import { virtualGroupApi, type VirtualGroup } from '@/api/virtualGroup'

export const useVirtualGroupStore = defineStore('virtualGroup', () => {
  const groups = ref<VirtualGroup[]>([])
  const loading = ref(false)

  const fetchGroups = async () => {
    loading.value = true
    try {
      groups.value = await virtualGroupApi.list()
    } finally {
      loading.value = false
    }
  }

  const deleteGroup = async (id: string) => {
    await virtualGroupApi.delete(id)
  }

  const activateGroup = async (id: string) => {
    await virtualGroupApi.activate(id)
  }

  const deactivateGroup = async (id: string) => {
    await virtualGroupApi.deactivate(id)
  }

  return { groups, loading, fetchGroups, deleteGroup, activateGroup, deactivateGroup }
})
