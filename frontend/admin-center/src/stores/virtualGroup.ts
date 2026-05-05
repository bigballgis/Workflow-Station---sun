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

  return { groups, loading, fetchGroups, deleteGroup }
})
