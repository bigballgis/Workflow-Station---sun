import { defineStore } from 'pinia'
import { ref } from 'vue'
import { functionUnitApi, type FunctionUnit, type FunctionUnitRequest } from '@/api/functionUnit'

export const useFunctionUnitStore = defineStore('functionUnit', () => {
  const list = ref<FunctionUnit[]>([])
  const current = ref<FunctionUnit | null>(null)
  const loading = ref(false)
  const total = ref(0)

  async function fetchList(params: { name?: string; status?: string; page?: number; size?: number }) {
    loading.value = true
    try {
      const res = await functionUnitApi.list(params)
      list.value = res.data.content
      total.value = res.data.totalElements
    } finally {
      loading.value = false
    }
  }

  async function fetchById(id: number) {
    loading.value = true
    try {
      const res = await functionUnitApi.getById(id)
      current.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function create(data: FunctionUnitRequest) {
    const res = await functionUnitApi.create(data)
    return res.data
  }

  async function update(id: number, data: FunctionUnitRequest) {
    const res = await functionUnitApi.update(id, data)
    return res.data
  }

  async function remove(id: number) {
    await functionUnitApi.delete(id)
  }

  async function publish(id: number, changeLog?: string) {
    const res = await functionUnitApi.publish(id, changeLog)
    return res.data
  }

  async function clone(id: number, newName: string) {
    const res = await functionUnitApi.clone(id, newName)
    return res.data
  }

  return { list, current, loading, total, fetchList, fetchById, create, update, remove, publish, clone }
})
