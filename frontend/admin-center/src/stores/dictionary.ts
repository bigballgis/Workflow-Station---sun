import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dictionaryApi, type Dictionary } from '@/api/dictionary'

export const useDictionaryStore = defineStore('dictionary', () => {
  const dictionaries = ref<Dictionary[]>([])
  const loading = ref(false)

  const fetchDictionaries = async (params?: { type?: string; status?: string }) => {
    loading.value = true
    try {
      dictionaries.value = await dictionaryApi.list(params?.type, params?.status)
    } finally {
      loading.value = false
    }
  }

  const fetchItems = async (dictId: string) => {
    return await dictionaryApi.getItems(dictId)
  }

  const deleteItem = async (itemId: string) => {
    await dictionaryApi.deleteItem(itemId)
  }

  return { dictionaries, loading, fetchDictionaries, fetchItems, deleteItem }
})
