import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi, User, UserQuery, PageResult } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const users = ref<User[]>([])
  const total = ref(0)
  const loading = ref(false)
  const currentUser = ref<User | null>(null)

  const fetchUsers = async (query: UserQuery) => {
    loading.value = true
    try {
      const result: PageResult<User> = await userApi.list(query)
      users.value = result.content
      total.value = result.totalElements
    } finally {
      loading.value = false
    }
  }

  const createUser = async (data: any) => {
    await userApi.create(data)
  }

  const updateUser = async (id: string, data: any) => {
    await userApi.update(id, data)
  }

  const deleteUser = async (id: string) => {
    await userApi.delete(id)
  }

  const changeStatus = async (id: string, action: 'enable' | 'disable' | 'lock' | 'unlock') => {
    await userApi[action](id)
  }

  return { users, total, loading, currentUser, fetchUsers, createUser, updateUser, deleteUser, changeStatus }
})
